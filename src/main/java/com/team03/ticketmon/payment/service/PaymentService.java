package com.team03.ticketmon.payment.service;

import java.util.UUID;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.team03.ticketmon._global.config.AppProperties;
import com.team03.ticketmon.concert.domain.Booking;
import com.team03.ticketmon.concert.domain.enums.BookingStatus;
import com.team03.ticketmon.concert.repository.BookingRepository;
import com.team03.ticketmon.payment.config.TossPaymentsProperties;
import com.team03.ticketmon.payment.domain.Payment;
import com.team03.ticketmon.payment.domain.dto.PaymentExecutionResponse;
import com.team03.ticketmon.payment.domain.dto.PaymentRequest;
import com.team03.ticketmon.payment.repository.PaymentRepository;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class PaymentService {

	private final BookingRepository bookingRepository;
	private final PaymentRepository paymentRepository;
	private final TossPaymentsProperties tossPaymentsProperties;
	private final AppProperties appProperties;

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
}
