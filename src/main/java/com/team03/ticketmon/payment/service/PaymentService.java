package com.team03.ticketmon.payment.service;

import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.Base64;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

import org.springframework.http.HttpStatusCode;
import org.springframework.http.MediaType;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.reactive.function.client.WebClient;

import com.team03.ticketmon._global.config.AppProperties;
import com.team03.ticketmon._global.exception.BusinessException;
import com.team03.ticketmon._global.exception.ErrorCode;
import com.team03.ticketmon.booking.domain.Booking;
import com.team03.ticketmon.booking.domain.BookingStatus;
import com.team03.ticketmon.booking.repository.BookingRepository;
import com.team03.ticketmon.payment.config.TossPaymentsProperties;
import com.team03.ticketmon.payment.domain.entity.Payment;
import com.team03.ticketmon.payment.domain.entity.PaymentCancelHistory;
import com.team03.ticketmon.payment.domain.enums.PaymentStatus;
import com.team03.ticketmon.payment.dto.PaymentCancelRequest;
import com.team03.ticketmon.payment.dto.PaymentConfirmRequest;
import com.team03.ticketmon.payment.dto.PaymentExecutionResponse;
import com.team03.ticketmon.payment.dto.PaymentHistoryDto;
import com.team03.ticketmon.payment.repository.PaymentCancelHistoryRepository;
import com.team03.ticketmon.payment.repository.PaymentRepository;
import com.team03.ticketmon.user.repository.UserRepository;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import reactor.core.publisher.Mono;

@Slf4j
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class PaymentService {

	private final BookingRepository bookingRepository;
	private final PaymentRepository paymentRepository;
	private final PaymentCancelHistoryRepository paymentCancelHistoryRepository;
	private final TossPaymentsProperties tossPaymentsProperties;
	private final AppProperties appProperties;
	private final WebClient webClient;
	private final UserRepository userRepository;

	@Transactional
	public PaymentExecutionResponse initiatePayment(Booking booking, Long currentUserId) {
		if (booking == null) {
			throw new BusinessException(ErrorCode.BOOKING_NOT_FOUND);
		}
		if (!booking.getUserId().equals(currentUserId)) {
			log.warn("사용자 {}가 본인 소유가 아닌 예매(ID:{}) 결제를 시도했습니다.", currentUserId, booking.getBookingId());
			throw new AccessDeniedException("본인의 예매만 결제할 수 있습니다.");
		}
		if (booking.getStatus() != BookingStatus.PENDING_PAYMENT) {
			throw new BusinessException(ErrorCode.INVALID_BOOKING_STATUS_FOR_PAYMENT); // ErrorCode에 추가 필요
		}
		if (booking.getConcert() == null) {
			throw new IllegalStateException("예매에 연결된 콘서트 정보가 없습니다. Booking ID: " + booking.getBookingId());
		}

		Payment paymentToUse = paymentRepository.findByBooking(booking)
			.filter(p -> p.getStatus() == PaymentStatus.PENDING)
			.orElseGet(() -> {
				log.info("신규 결제 정보를 생성합니다. bookingNumber: {}", booking.getBookingNumber());
				String orderId = UUID.randomUUID().toString();
				return paymentRepository.save(Payment.builder()
					.booking(booking)
					.userId(booking.getUserId())
					.orderId(orderId)
					.amount(booking.getTotalAmount())
					.build());
			});

		String customerName = userRepository.findById(currentUserId)
			.map(user -> user.getNickname())
			.orElse("사용자 " + currentUserId);

		return PaymentExecutionResponse.builder()
			.orderId(paymentToUse.getOrderId())
			.bookingNumber(booking.getBookingNumber())
			.orderName(booking.getConcert().getTitle())
			.amount(booking.getTotalAmount())
			.customerName(customerName)
			.clientKey(tossPaymentsProperties.clientKey())
			.successUrl(appProperties.baseUrl() + "/api/v1/payments/success")
			.failUrl(appProperties.baseUrl() + "/api/v1/payments/fail")
			.build();
	}

	@Transactional
	public void confirmPayment(PaymentConfirmRequest confirmRequest) {
		Payment payment = paymentRepository.findByOrderId(confirmRequest.getOrderId())
			.orElseThrow(() -> new IllegalArgumentException("존재하지 않는 주문 ID 입니다: " + confirmRequest.getOrderId()));

		if (payment.getAmount().compareTo(confirmRequest.getAmount()) != 0) {
			throw new IllegalArgumentException("결제 금액이 일치하지 않습니다.");
		}
		if (payment.getStatus() != PaymentStatus.PENDING) {
			log.warn("이미 처리된 주문에 대한 승인 요청 무시: orderId={}", confirmRequest.getOrderId());
			return;
		}

		String encodedSecretKey = Base64.getEncoder()
			.encodeToString((tossPaymentsProperties.secretKey() + ":").getBytes(StandardCharsets.UTF_8));
		callTossConfirmApi(confirmRequest, encodedSecretKey, confirmRequest.getOrderId())
			.doOnSuccess(tossResponse -> {
				LocalDateTime approvedAt = parseDateTime(tossResponse.get("approvedAt"));
				payment.complete(confirmRequest.getPaymentKey(), approvedAt);
				payment.getBooking().confirm();
				log.info("결제 승인 완료: orderId={}", payment.getOrderId());
			})
			.doOnError(e -> {
				log.error("결제 승인 API 호출 중 오류 발생: orderId={}, 오류={}", confirmRequest.getOrderId(), e.getMessage());
				payment.fail();
				throw new RuntimeException("결제 승인에 실패했습니다.", e);
			})
			.block();
	}

	@Transactional
	public void handlePaymentFailure(String orderId, String errorCode, String errorMessage) {
		paymentRepository.findByOrderId(orderId).ifPresent(payment -> {
			if (payment.getStatus() == PaymentStatus.PENDING) {
				payment.fail();
				payment.getBooking().cancel();
				log.info("결제 실패 상태로 변경 완료: orderId={}", orderId);
			}
		});
	}

	/**
	 * Facade로부터 받은 Booking 객체에 대해 결제 취소를 실행하고 상태를 변경
	 * @param booking 취소할 Booking 엔티티
	 * @param cancelRequest 취소 요청 DTO (사유 등)
	 * @param currentUserId 현재 로그인된 사용자 ID (소유권 검증용)
	 */
	@Transactional
	public void cancelPayment(Booking booking, PaymentCancelRequest cancelRequest, Long currentUserId) {
		if (booking == null) {
			throw new BusinessException(ErrorCode.BOOKING_NOT_FOUND);
		}

		Payment payment = booking.getPayment();
		if (payment == null) {
			log.warn("예매(ID:{})에 연결된 결제 정보가 없어 결제 취소를 건너뜁니다.", booking.getBookingId());
			return;
		}

		if (!payment.getUserId().equals(currentUserId)) {
			log.warn("사용자 {}가 본인 소유가 아닌 결제(orderId:{}) 취소를 시도했습니다.", currentUserId, payment.getOrderId());
			throw new AccessDeniedException("본인의 결제만 취소할 수 있습니다.");
		}

		if (payment.getStatus() != PaymentStatus.DONE && payment.getStatus() != PaymentStatus.PARTIAL_CANCELED) {
			log.info("취소할 수 없는 상태의 결제입니다. (상태: {})", payment.getStatus());
			return;
		}

		// 💡 [통합] internalCancel의 로직을 이곳으로 통합합니다.
		String encodedSecretKey = Base64.getEncoder()
			.encodeToString((tossPaymentsProperties.secretKey() + ":").getBytes(StandardCharsets.UTF_8));
		callTossCancelApi(payment.getPaymentKey(), cancelRequest.getCancelReason(), encodedSecretKey)
			.doOnSuccess(tossResponse -> {
				payment.cancel();

				List<Map<String, Object>> cancels = (List<Map<String, Object>>)tossResponse.get("cancels");
				if (cancels != null && !cancels.isEmpty()) {
					Map<String, Object> lastCancel = cancels.get(cancels.size() - 1);
					PaymentCancelHistory history = PaymentCancelHistory.builder()
						.payment(payment)
						.transactionKey((String)lastCancel.get("transactionKey"))
						.cancelAmount(new BigDecimal(lastCancel.get("cancelAmount").toString()))
						.cancelReason((String)lastCancel.get("cancelReason"))
						.canceledAt(parseDateTime(lastCancel.get("canceledAt")))
						.build();
					paymentCancelHistoryRepository.save(history);
				}
				log.info("결제 취소 완료: orderId={}", payment.getOrderId());
			})
			.doOnError(e -> {
				log.error("결제 취소 중 오류 발생: orderId={}, 오류={}", payment.getOrderId(), e.getMessage(), e);
				throw new RuntimeException("결제 취소에 실패했습니다. (내부 오류)", e);
			})
			.block();
	}

	// 💡 [제거] private void internalCancel(...) 메서드는 위 cancelPayment 메서드와 통합되었으므로 삭제합니다.

	@Transactional(readOnly = true)
	public List<PaymentHistoryDto> getPaymentHistoryByUserId(Long userId) {
		List<Payment> payments = paymentRepository.findByUserId(userId);
		return payments.stream()
			.map(PaymentHistoryDto::new) // 💡 [수정] fromEntity 대신 DTO의 생성자 직접 사용
			.collect(Collectors.toList());
	}

	private Mono<Map> callTossConfirmApi(PaymentConfirmRequest confirmRequest, String encodedSecretKey,
		String idempotencyKey) {
		return webClient.post()
			.uri("https://api.tosspayments.com/v1/payments/confirm")
			.header("Authorization", "Basic " + encodedSecretKey)
			.header("Idempotency-Key", idempotencyKey)
			.contentType(MediaType.APPLICATION_JSON)
			.bodyValue(Map.of(
				"paymentKey", confirmRequest.getPaymentKey(),
				"orderId", confirmRequest.getOrderId(),
				"amount", confirmRequest.getAmount()
			))
			.retrieve()
			.onStatus(HttpStatusCode::isError, response -> response.bodyToMono(String.class)
				.flatMap(errorBody -> Mono.error(new RuntimeException("토스페이먼츠 API 호출 실패: " + errorBody))))
			.bodyToMono(Map.class);
	}

	private Mono<Map> callTossCancelApi(String paymentKey, String cancelReason, String encodedSecretKey) {
		return webClient.post()
			.uri("https://api.tosspayments.com/v1/payments/{paymentKey}/cancel", paymentKey)
			.header("Authorization", "Basic " + encodedSecretKey)
			.contentType(MediaType.APPLICATION_JSON)
			.bodyValue(Map.of("cancelReason", cancelReason))
			.retrieve()
			.onStatus(HttpStatusCode::isError, response -> response.bodyToMono(String.class)
				.flatMap(errorBody -> Mono.error(new RuntimeException("결제 취소에 실패했습니다. (토스 응답 오류): " + errorBody))))
			.bodyToMono(Map.class);
	}

	private LocalDateTime parseDateTime(Object dateTimeObj) {
		if (dateTimeObj instanceof String dateTimeStr) {
			try {
				return OffsetDateTime.parse(dateTimeStr, DateTimeFormatter.ISO_OFFSET_DATE_TIME).toLocalDateTime();
			} catch (DateTimeParseException e) {
				log.warn("날짜 파싱 실패 (ISO_OFFSET_DATE_TIME): {}. 다른 포맷 시도.", dateTimeStr);
				try {
					return LocalDateTime.parse(dateTimeStr, DateTimeFormatter.ISO_DATE_TIME);
				} catch (DateTimeParseException ex) {
					log.error("날짜 파싱 최종 실패: {}", dateTimeStr, ex);
				}
			}
		}
		return LocalDateTime.now();
	}

	/**
	 * 웹훅을 통한 결제 상태 갱신
	 * - 외부 시스템(토스 등)에서 결제 상태 변경 알림이 오면 상태를 업데이트
	 * @param orderId 업데이트할 주문 ID
	 * @param status 새로운 결제 상태 문자열
	 */
	@Transactional
	public void updatePaymentStatusByWebhook(String orderId, String status) {
		log.info("웹훅을 통한 결제 상태 업데이트 시도: orderId={}, status={}", orderId, status);

		Payment payment = paymentRepository.findByOrderId(orderId)
			.orElseThrow(() -> new BusinessException(ErrorCode.RESOURCE_NOT_FOUND, "웹훅 처리: 결제 정보를 찾을 수 없습니다."));

		PaymentStatus newStatus = PaymentStatus.valueOf(status.toUpperCase()); // 대소문자 문제 방지

		// 이미 같은 상태이거나, PENDING에서 실패로 가는 경우 등을 고려
		if (payment.getStatus() == newStatus) {
			log.warn("웹훅: 결제 {} 상태 변경 없음 (현재: {}, 요청: {}). 처리 무시.", orderId, payment.getStatus(), newStatus);
			return;
		}

		// 상태 전환 로직 (좀 더 견고하게 변경)
		if (newStatus == PaymentStatus.DONE) {
			if (payment.getStatus() == PaymentStatus.PENDING) {
				payment.complete(payment.getPaymentKey(), LocalDateTime.now()); // paymentKey가 null일 수 있으니 주의
				payment.getBooking().confirm();
				log.info("웹훅: 결제 {} 상태 PENDING -> DONE 업데이트 완료", orderId);
			} else {
				log.warn("웹훅: 결제 {} 상태 DONE으로 변경 실패 (현재: {}). 처리 무시.", orderId, payment.getStatus());
			}
		} else if (newStatus == PaymentStatus.CANCELED) {
			// DONE -> CANCELED, PENDING -> CANCELED 모두 처리
			if (payment.getStatus() == PaymentStatus.DONE || payment.getStatus() == PaymentStatus.PENDING
				|| payment.getStatus() == PaymentStatus.PARTIAL_CANCELED) {
				payment.cancel();
				payment.getBooking().cancel();
				log.info("웹훅: 결제 {} 상태 {} -> CANCELED 업데이트 완료", orderId, payment.getStatus());
			} else {
				log.warn("웹훅: 결제 {} 상태 CANCELED로 변경 실패 (현재: {}). 처리 무시.", orderId, payment.getStatus());
			}
		} else if (newStatus == PaymentStatus.FAILED || newStatus == PaymentStatus.EXPIRED) {
			if (payment.getStatus() == PaymentStatus.PENDING) {
				payment.fail();
				payment.getBooking().cancel();
				log.info("웹훅: 결제 {} 상태 PENDING -> {} 업데이트 완료", orderId, newStatus);
			} else {
				log.warn("웹훅: 결제 {} 상태 {}으로 변경 실패 (현재: {}). 처리 무시.", orderId, newStatus, payment.getStatus());
			}
		} else {
			log.warn("웹훅: 결제 {} 상태 {} 변경 요청 처리 불가. 현재: {}.", orderId, newStatus, payment.getStatus());
		}
	}

	// private LocalDateTime parseDateTime(Object dateTimeObj) { ... } // 이 유틸 메서드가 없다면 추가해주세요.
	// private Mono<Map> callTossConfirmApi(...) { ... } // 이 유틸 메서드가 없다면 추가해주세요.
	// private Mono<Map> callTossCancelApi(...) { ... } // 이 유틸 메서드가 없다면 추가해주세요.
}

