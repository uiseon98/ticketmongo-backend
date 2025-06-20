package com.team03.ticketmon.payment.service;

import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.Base64;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.stream.Collectors;

import org.springframework.http.HttpStatusCode;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.reactive.function.client.WebClient;

import com.team03.ticketmon._global.config.AppProperties;
import com.team03.ticketmon.concert.domain.Booking;
import com.team03.ticketmon.concert.domain.enums.BookingStatus;
import com.team03.ticketmon.concert.repository.BookingRepository;
import com.team03.ticketmon.payment.config.TossPaymentsProperties;
import com.team03.ticketmon.payment.domain.entity.Payment;
import com.team03.ticketmon.payment.domain.entity.PaymentCancelHistory;
import com.team03.ticketmon.payment.domain.enums.PaymentStatus;
import com.team03.ticketmon.payment.dto.PaymentCancelRequest;
import com.team03.ticketmon.payment.dto.PaymentConfirmRequest;
import com.team03.ticketmon.payment.dto.PaymentExecutionResponse;
import com.team03.ticketmon.payment.dto.PaymentHistoryDto;
import com.team03.ticketmon.payment.dto.PaymentRequest;
import com.team03.ticketmon.payment.repository.PaymentCancelHistoryRepository;
import com.team03.ticketmon.payment.repository.PaymentRepository;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import reactor.core.publisher.Mono;

@Slf4j
@Service
@RequiredArgsConstructor
public class PaymentService {

	private final BookingRepository bookingRepository;
	private final PaymentRepository paymentRepository;
	private final PaymentCancelHistoryRepository paymentCancelHistoryRepository;
	private final TossPaymentsProperties tossPaymentsProperties;
	private final AppProperties appProperties;
	private final WebClient webClient;

	@Transactional
	public PaymentExecutionResponse initiatePayment(PaymentRequest paymentRequest) {
		Booking booking = bookingRepository.findByBookingNumber(paymentRequest.getBookingNumber())
			.orElseThrow(() -> new IllegalArgumentException("존재하지 않는 예매 번호입니다."));

		if (booking.getStatus() != BookingStatus.PENDING_PAYMENT) {
			throw new IllegalStateException("결제를 진행할 수 없는 예매 상태입니다.");
		}

		Optional<Payment> existingPaymentOpt = paymentRepository.findByBooking(booking);

		Payment paymentToUse;

		if (existingPaymentOpt.isPresent()) {
			paymentToUse = existingPaymentOpt.get();
			log.info("기존 결제 정보를 재사용합니다. orderId: {}", paymentToUse.getOrderId());
		} else {
			String orderId = UUID.randomUUID().toString();
			paymentToUse = Payment.builder()
				.booking(booking)
				.orderId(orderId)
				.amount(booking.getTotalAmount())
				.build();
			paymentRepository.save(paymentToUse);
			log.info("신규 결제 정보를 생성합니다. orderId: {}", paymentToUse.getOrderId());
		}

		return PaymentExecutionResponse.builder()
			.orderId(paymentToUse.getOrderId())
			.bookingNumber(booking.getBookingNumber())
			.orderName(booking.getConcert().getTitle())
			.amount(booking.getTotalAmount())
			.customerName(booking.getUserId().toString())
			.clientKey(tossPaymentsProperties.clientKey())
			.successUrl(appProperties.baseUrl() + "/api/v1/payments/success")
			.failUrl(appProperties.baseUrl() + "/api/v1/payments/fail")
			.build();
	}

	@Transactional
	public void confirmPayment(PaymentConfirmRequest confirmRequest) {
		Payment payment = paymentRepository.findByOrderId(confirmRequest.getOrderId())
			.orElseThrow(() -> new IllegalArgumentException("존재하지 않는 주문 ID 입니다: " + confirmRequest.getOrderId()));

		log.info("금액 검증 시작: DB 금액 = {}, 요청 금액 = {}", payment.getAmount(), confirmRequest.getAmount());
		if (payment.getAmount().compareTo(confirmRequest.getAmount()) != 0) {
			log.error("결제 금액 불일치 오류! DB 금액: {}, 요청 금액: {}", payment.getAmount(), confirmRequest.getAmount());
			throw new IllegalArgumentException("결제 금액이 일치하지 않습니다.");
		}

		Map<String, Object> requestBody = Map.of(
			"paymentKey", confirmRequest.getPaymentKey(),
			"orderId", confirmRequest.getOrderId(),
			"amount", confirmRequest.getAmount()
		);
		String encodedSecretKey = Base64.getEncoder()
			.encodeToString((tossPaymentsProperties.secretKey() + ":").getBytes(StandardCharsets.UTF_8));

		// 💡 [변경] WebClient 호출 부분을 새로운 메소드로 분리하여 호출합니다.
		callTossConfirmApi(requestBody, encodedSecretKey)
			.doOnSuccess(tossResponse -> {
				String paymentKeyFromToss = (String)tossResponse.get("paymentKey");
				LocalDateTime approvedAtFromToss = null;
				Object approvedAtObj = tossResponse.get("approvedAt");
				if (approvedAtObj instanceof String) {
					try {
						approvedAtFromToss = LocalDateTime.parse((String)approvedAtObj,
							DateTimeFormatter.ISO_OFFSET_DATE_TIME);
					} catch (Exception e) {
						try {
							approvedAtFromToss = LocalDateTime.parse((String)approvedAtObj,
								DateTimeFormatter.ISO_DATE_TIME);
						} catch (Exception ex) {
							log.warn("approvedAt 파싱 실패: {}, 기본 현재 시간 사용", approvedAtObj);
							approvedAtFromToss = LocalDateTime.now();
						}
					}
				} else {
					approvedAtFromToss = LocalDateTime.now();
				}

				payment.complete(paymentKeyFromToss, approvedAtFromToss);
				payment.getBooking().confirm();
				log.info("결제 승인 완료: orderId={}", payment.getOrderId());
			})
			.block();
	}

	/**
	 * 💡 [추가] 토스페이먼츠 결제 승인 API를 호출하는 책임을 분리한 메소드
	 * 테스트 용이성을 위해 public으로 선언합니다.
	 */
	public Mono<Map> callTossConfirmApi(Map<String, Object> requestBody, String encodedSecretKey) {
		return webClient.post()
			.uri("https://api.tosspayments.com/v1/payments/confirm")
			.header("Authorization", "Basic " + encodedSecretKey)
			.contentType(MediaType.APPLICATION_JSON)
			.bodyValue(requestBody)
			.retrieve()
			.onStatus(HttpStatusCode::isError, response ->
				response.bodyToMono(String.class)
					.flatMap(errorBody -> {
						log.error("토스페이먼츠 API 호출 실패: status={}, body={}", response.statusCode(), errorBody);
						return Mono.error(new RuntimeException("결제 승인에 실패했습니다. (토스 응답 오류)"));
					})
			)
			.bodyToMono(Map.class);
	}

	@Transactional
	public void handlePaymentFailure(String orderId, String errorCode, String errorMessage) {
		Payment payment = paymentRepository.findByOrderId(orderId)
			.orElseThrow(() -> new IllegalArgumentException("존재하지 않는 주문 ID 입니다: " + orderId));

		if (payment.getStatus() == PaymentStatus.PENDING) {
			payment.fail();
			payment.getBooking().cancel();
			log.info("결제 실패 상태로 변경 완료: orderId={}, errorCode={}, errorMessage={}", orderId, errorCode, errorMessage);
		} else {
			log.warn("이미 처리된 주문에 대한 실패 처리 요청: orderId={}, 현재 상태: {}", orderId, payment.getStatus());
		}
	}

	@Transactional
	public void cancelPayment(String orderId, PaymentCancelRequest cancelRequest) {
		Payment payment = paymentRepository.findByOrderId(orderId)
			.orElseThrow(() -> new IllegalArgumentException("존재하지 않는 주문 ID 입니다: " + orderId));

		if (payment.getStatus() == PaymentStatus.CANCELED) {
			throw new IllegalStateException("이미 취소된 결제입니다.");
		}

		String encodedSecretKey = Base64.getEncoder()
			.encodeToString((tossPaymentsProperties.secretKey() + ":").getBytes(StandardCharsets.UTF_8));

		Map<String, Object> tossCancelResponse = webClient.post()
			.uri("https://api.tosspayments.com/v1/payments/" + payment.getPaymentKey() + "/cancel")
			.header("Authorization", "Basic " + encodedSecretKey)
			.contentType(MediaType.APPLICATION_JSON)
			.bodyValue(Map.of("cancelReason", cancelRequest.getCancelReason()))
			.retrieve()
			.onStatus(HttpStatusCode::isError, response ->
				response.bodyToMono(String.class)
					.flatMap(errorBody -> {
						log.error("토스페이먼츠 취소 API 호출 실패: status={}, body={}", response.statusCode(), errorBody);
						return Mono.error(new RuntimeException("결제 취소에 실패했습니다. (토스 응답 오류)"));
					})
			)
			.bodyToMono(Map.class)
			.block();

		payment.cancel();
		payment.getBooking().cancel();

		String transactionKey = null;
		BigDecimal cancelAmount = BigDecimal.ZERO;
		LocalDateTime canceledAt = null;

		List<Map<String, Object>> cancels = (List<Map<String, Object>>)tossCancelResponse.get("cancels");
		if (cancels != null && !cancels.isEmpty()) {
			Map<String, Object> lastCancel = cancels.get(cancels.size() - 1);
			transactionKey = (String)lastCancel.get("transactionKey");

			Object amountObj = lastCancel.get("cancelAmount");
			if (amountObj instanceof Integer) {
				cancelAmount = BigDecimal.valueOf((Integer)amountObj);
			} else if (amountObj instanceof Double) {
				cancelAmount = BigDecimal.valueOf((Double)amountObj);
			} else if (amountObj != null) {
				cancelAmount = new BigDecimal(amountObj.toString());
			}

			Object canceledAtObj = lastCancel.get("canceledAt");
			if (canceledAtObj instanceof String) {
				try {
					canceledAt = LocalDateTime.parse((String)canceledAtObj, DateTimeFormatter.ISO_OFFSET_DATE_TIME);
				} catch (DateTimeParseException e) {
					log.warn("canceledAt 파싱 실패 (ISO_OFFSET_DATE_TIME): {}, 다른 포맷 시도", canceledAtObj);
					try {
						canceledAt = LocalDateTime.parse((String)canceledAtObj, DateTimeFormatter.ISO_DATE_TIME);
					} catch (DateTimeParseException ex) {
						log.error("canceledAt 파싱 최종 실패: {}", canceledAtObj, ex);
						canceledAt = LocalDateTime.now();
					}
				}
			}
		} else {
			log.warn("토스페이먼츠 취소 응답에 'cancels' 정보가 없거나 비어 있습니다. orderId: {}", payment.getOrderId());
		}

		if (canceledAt == null) {
			canceledAt = LocalDateTime.now();
		}

		PaymentCancelHistory history = PaymentCancelHistory.builder()
			.payment(payment)
			.transactionKey(transactionKey)
			.cancelAmount(cancelAmount)
			.cancelReason(cancelRequest.getCancelReason())
			.canceledAt(canceledAt)
			.build();
		paymentCancelHistoryRepository.save(history);

		log.info("결제 취소 완료: orderId={}", orderId);
	}

	@Transactional
	public void updatePaymentStatusByWebhook(String orderId, String status) {
		Payment payment = paymentRepository.findByOrderId(orderId)
			.orElseThrow(() -> {
				log.warn("웹훅 처리: 존재하지 않는 주문 ID 입니다 - {}", orderId);
				return new IllegalArgumentException("존재하지 않는 주문 ID 입니다: " + orderId);
			});

		PaymentStatus newStatus = PaymentStatus.valueOf(status.toUpperCase());

		if (payment.getStatus() == newStatus) {
			log.info("웹훅 처리: 주문 ID {}의 상태가 이미 {}입니다. 변경 없음.", orderId, newStatus);
			return;
		}

		if (newStatus == PaymentStatus.DONE) {
			payment.complete(payment.getPaymentKey(), LocalDateTime.now());
			payment.getBooking().confirm();
		} else if (newStatus == PaymentStatus.CANCELED) {
			payment.cancel();
			payment.getBooking().cancel();
		}

		log.info("주문 ID {} 의 결제 상태가 웹훅을 통해 {} 로 변경되었습니다.", orderId, newStatus);
	}

	@Transactional(readOnly = true)
	public List<PaymentHistoryDto> getPaymentHistoryByUserId(Long userId) {
		return paymentRepository.findByBooking_UserId(userId)
			.stream()
			.map(PaymentHistoryDto::new)
			.collect(Collectors.toList());
	}
}
