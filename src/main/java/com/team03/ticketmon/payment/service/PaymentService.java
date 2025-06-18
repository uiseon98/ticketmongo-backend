package com.team03.ticketmon.payment.service;

import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.Map;
import java.util.UUID;

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
import com.team03.ticketmon.payment.dto.PaymentConfirmRequest;
import com.team03.ticketmon.payment.dto.PaymentExecutionResponse;
import com.team03.ticketmon.payment.dto.PaymentRequest;
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

}
