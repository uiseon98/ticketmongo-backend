package com.team03.ticketmon.payment.service;

import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.List;
import java.util.Map;
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
	private final TossPaymentsProperties tossPaymentsProperties;
	private final AppProperties appProperties;
	private final WebClient webClient; // webclient 주입추가
	private final PaymentCancelHistoryRepository paymentCancelHistoryRepository;

	@Transactional
	public PaymentExecutionResponse initiatePayment(PaymentRequest paymentRequest) {
		// 1. 예매 번호로 예매 정보 조회
		Booking booking = bookingRepository.findByBookingNumber(paymentRequest.getBookingNumber())
			.orElseThrow(() -> new IllegalArgumentException("존재하지 않는 예매 번호입니다."));

		// 2. 이미 결제되었거나 취소된 예매인지 확인하여 멱등성 확보
		if (booking.getStatus() != BookingStatus.PENDING_PAYMENT) {
			throw new IllegalStateException("결제를 진행할 수 없는 예매 상태입니다.");
		}

		// 3. 토스페이먼츠용 주문 ID 생성 (고유해야 함)
		String orderId = UUID.randomUUID().toString();

		// 4. Payment 엔티티 생성 및 저장
		Payment payment = Payment.builder()
			.booking(booking)
			.orderId(orderId)
			.amount(booking.getTotalAmount()) // 예매 정보에 있는 실제 금액 사용
			.build();
		paymentRepository.save(payment);

		// 5. 프론트엔드(React)에 전달할 결제창 호출 정보 생성
		return PaymentExecutionResponse.builder()
			.orderId(orderId)
			.bookingNumber(booking.getBookingNumber())
			.orderName(booking.getConcert().getTitle()) // 예시: 콘서트 제목
			.amount(booking.getTotalAmount())
			.customerName(booking.getUserId().toString()) // 예시: 사용자 ID 또는 이름
			.clientKey(tossPaymentsProperties.clientKey())
			.successUrl(appProperties.baseUrl() + "/api/v1/payments/success")
			.failUrl(appProperties.baseUrl() + "/api/v1/payments/fail")
			.build();
	}

	@Transactional
	public void confirmPayment(PaymentConfirmRequest confirmRequest) {
		// 1. 주문 ID로 우리 DB의 결제 정보 조회
		Payment payment = paymentRepository.findByOrderId(confirmRequest.getOrderId())
			.orElseThrow(() -> new IllegalArgumentException("존재하지 않는 주문 ID 입니다: " + confirmRequest.getOrderId()));

		// 2. 금액 위변조 확인: 요청된 금액과 DB에 저장된 금액이 일치하는지 검증 (매우 중요)
		if (!payment.getAmount().equals(confirmRequest.getAmount())) {
			throw new IllegalArgumentException("결제 금액이 일치하지 않습니다.");
		}

		// 3. 토스페이먼츠에 보낼 요청 본문 생성
		Map<String, Object> requestBody = Map.of(
			"paymentKey", confirmRequest.getPaymentKey(),
			"orderId", confirmRequest.getOrderId(),
			"amount", confirmRequest.getAmount()
		);

		// 4. Basic 인증을 위한 시크릿 키 인코딩
		String encodedSecretKey = Base64.getEncoder()
			.encodeToString((tossPaymentsProperties.secretKey() + ":").getBytes(StandardCharsets.UTF_8));

		// 5. WebClient를 사용하여 토스페이먼츠 '결제 승인 API' 비동기 호출
		webClient.post()
			.uri("https://api.tosspayments.com/v1/payments/confirm")
			.header("Authorization", "Basic " + encodedSecretKey)
			.contentType(MediaType.APPLICATION_JSON)
			.bodyValue(requestBody)
			.retrieve()
			// 6. API 호출 실패 시 예외 처리
			.onStatus(HttpStatusCode::isError, response ->
				response.bodyToMono(String.class)
					.flatMap(errorBody -> {
						log.error("토스페이먼츠 API 호출 실패: status={}, body={}", response.statusCode(), errorBody);
						// 실패 시 Payment와 Booking 상태 업데이트
						payment.fail();
						payment.getBooking().cancel(); // 예시: 예매를 취소 상태로 변경
						return Mono.error(new RuntimeException("결제 승인에 실패했습니다."));
					})
			)
			// 7. API 호출 성공 시 DB 상태 업데이트
			.bodyToMono(Void.class) // 성공 응답 본문이 필요 없으면 Void.class
			.doOnSuccess(response -> {
				payment.complete(confirmRequest.getPaymentKey()); // paymentKey와 상태 업데이트
				payment.getBooking().confirm(); // Booking 상태를 '확정'으로 변경
				log.info("결제 승인 완료: orderId={}", payment.getOrderId());
			})
			.block(); // 비동기 작업이 끝날 때까지 대기 (Controller에서 RedirectView를 사용하므로 블로킹 방식 사용)
	}

	/**
	 * 결제 실패 시의 비즈니스 로직을 처리합니다.
	 */
	@Transactional
	public void handlePaymentFailure(String orderId, String errorCode, String errorMessage) {
		Payment payment = paymentRepository.findByOrderId(orderId)
			.orElseThrow(() -> new IllegalArgumentException("존재하지 않는 주문 ID 입니다: " + orderId));

		// 이미 최종 상태(성공 또는 취소)가 아니라면 '실패' 상태로 변경
		if (payment.getStatus() == PaymentStatus.PENDING) {
			payment.fail();
			payment.getBooking().cancel(); // 예매도 취소 상태로 변경
			log.info("결제 실패 상태로 변경 완료: orderId={}, errorCode={}, errorMessage={}", orderId, errorCode, errorMessage);
		} else {
			log.warn("이미 처리된 주문에 대한 실패 처리 요청: orderId={}, 현재 상태: {}", orderId, payment.getStatus());
		}
	}

	/**
	 * 결제를 취소합니다. (사용자 요청 또는 관리자 기능)
	 */
	@Transactional
	public void cancelPayment(String orderId, PaymentCancelRequest cancelRequest) {
		Payment payment = paymentRepository.findByOrderId(orderId)
			.orElseThrow(() -> new IllegalArgumentException("존재하지 않는 주문 ID 입니다: " + orderId));

		// 이미 취소된 결제인지 확인
		if (payment.getStatus() == PaymentStatus.CANCELED) {
			throw new IllegalStateException("이미 취소된 결제입니다.");
		}

		// 1. 토스페이먼츠 '결제 취소 API' 호출
		String encodedSecretKey = Base64.getEncoder()
			.encodeToString((tossPaymentsProperties.secretKey() + ":").getBytes(StandardCharsets.UTF_8));

		// API 호출 (실제 운영에서는 응답 DTO를 만들어 사용하는 것이 좋습니다)
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
						return Mono.error(new RuntimeException("결제 취소에 실패했습니다."));
					})
			)
			.bodyToMono(Map.class) // Map으로 응답을 받음
			.block();

		// 2. 우리 시스템 DB 상태 업데이트
		payment.cancel(); // Payment 상태를 CANCELED로 변경 (새 메소드 필요)
		payment.getBooking().cancel(); // Booking 상태도 CANCELED로 변경

		// 3. 결제 취소 이력 저장
		PaymentCancelHistory history = PaymentCancelHistory.builder()
			.payment(payment)
			.transactionKey((String)tossCancelResponse.get("transactionKey"))
			.cancelAmount(new BigDecimal(
				tossCancelResponse.get("balanceAmount").toString())) // 취소 후 남은 금액이 0이므로, 전체 취소 금액을 계산해야 함
			.cancelReason(cancelRequest.getCancelReason())
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

		PaymentStatus newStatus = PaymentStatus.valueOf(status);

		if (payment.getStatus() == newStatus) {
			log.info("웹훅 처리: 주문 ID {}의 상태가 이미 {}입니다. 변경 없음.", orderId, status);
			return;
		}

		// 웹훅을 통해 상태 업데이트
		if (newStatus == PaymentStatus.DONE) {
			payment.complete(payment.getPaymentKey()); // paymentKey는 이미 있거나, 가상계좌의 경우 별도 조회가 필요할 수 있음
			payment.getBooking().confirm();
		} else if (newStatus == PaymentStatus.CANCELED) {
			payment.cancel();
			payment.getBooking().cancel();
		}
		// 다른 상태에 대한 처리 로직 추가 가능

		log.info("주문 ID {} 의 결제 상태가 웹훅을 통해 {} 로 변경되었습니다.", orderId, status);
	}

	@Transactional(readOnly = true)
	public List<PaymentHistoryDto> getPaymentHistoryByUserId(Long userId) {
		// 사용자 ID로 직접 Payment 목록을 조회 (더 효율적)
		return paymentRepository.findByBooking_UserId(userId)
			.stream()
			.map(PaymentHistoryDto::new)
			.collect(Collectors.toList());
	}
}
