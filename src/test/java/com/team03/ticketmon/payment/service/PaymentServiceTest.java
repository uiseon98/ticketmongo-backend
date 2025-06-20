package com.team03.ticketmon.payment.service;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import static org.mockito.Mockito.eq;

import java.math.BigDecimal;
import java.util.Map;
import java.util.Optional;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.web.reactive.function.client.WebClient;

import com.team03.ticketmon._global.config.AppProperties;
import com.team03.ticketmon.concert.domain.Booking;
import com.team03.ticketmon.concert.domain.Concert;
import com.team03.ticketmon.concert.domain.enums.BookingStatus;
import com.team03.ticketmon.concert.repository.BookingRepository;
import com.team03.ticketmon.payment.config.TossPaymentsProperties;
import com.team03.ticketmon.payment.domain.entity.Payment;
import com.team03.ticketmon.payment.domain.enums.PaymentStatus;
import com.team03.ticketmon.payment.dto.PaymentConfirmRequest;
import com.team03.ticketmon.payment.repository.PaymentRepository;

import reactor.core.publisher.Mono;

@ExtendWith(MockitoExtension.class)
class PaymentServiceTest {

	@InjectMocks
	private PaymentService paymentService;

	@Mock
	private PaymentRepository paymentRepository;

	@Mock
	private BookingRepository bookingRepository;

	@Mock
	private WebClient webClient;
	@Mock
	private TossPaymentsProperties tossPaymentsProperties;
	@Mock
	private AppProperties appProperties;

	@Mock
	private WebClient.RequestBodyUriSpec requestBodyUriSpec;
	@Mock
	private WebClient.RequestBodySpec requestBodySpec;

	// 💡 [필수 수정] 문제가 되는 responseSpec Mock에 lenient = true 속성을 추가합니다.
	@Mock(lenient = true)
	private WebClient.ResponseSpec responseSpec;

	@Test
	@DisplayName("결제 승인 성공 테스트")
	void 결제_승인_성공_테스트() {
		// GIVEN
		String orderId = "test-order-id";
		String paymentKey = "test-payment-key";
		BigDecimal amount = new BigDecimal("55000");

		PaymentConfirmRequest confirmRequest = PaymentConfirmRequest.builder()
			.orderId(orderId)
			.paymentKey(paymentKey)
			.amount(amount)
			.build();

		Concert mockConcert = Concert.builder().title("테스트 콘서트").build();
		Booking mockBooking = Booking.builder()
			.bookingId(1L)
			.concert(mockConcert)
			.status(BookingStatus.PENDING_PAYMENT)
			.build();
		Payment mockPayment = Payment.builder().booking(mockBooking).orderId(orderId).amount(amount).build();

		when(paymentRepository.findByOrderId(orderId)).thenReturn(Optional.of(mockPayment));
		when(tossPaymentsProperties.secretKey()).thenReturn("test-secret-key");

		// WebClient Mocking 설정
		when(webClient.post()).thenReturn(requestBodyUriSpec);
		when(requestBodyUriSpec.uri(anyString())).thenReturn(requestBodySpec);
		when(requestBodySpec.header(any(), any())).thenReturn(requestBodySpec);
		when(requestBodySpec.contentType(any())).thenReturn(requestBodySpec);
		doReturn(requestBodySpec).when(requestBodySpec).bodyValue(any());
		when(requestBodySpec.retrieve()).thenReturn(responseSpec);
		when(responseSpec.onStatus(any(), any())).thenReturn(responseSpec);

		Map<String, Object> tossResponse = Map.of(
			"paymentKey", paymentKey,
			"approvedAt", "2025-06-21T12:00:00+09:00"
		);

		// doReturn 구문은 그대로 사용합니다.
		doReturn(Mono.just(tossResponse)).when(responseSpec).bodyToMono(eq(Map.class));

		// WHEN
		paymentService.confirmPayment(confirmRequest);

		// THEN
		verify(paymentRepository, times(1)).findByOrderId(orderId);
		verify(webClient.post(), times(1)).uri("https://api.tosspayments.com/v1/payments/confirm");
		assertEquals(PaymentStatus.DONE, mockPayment.getStatus());
		assertEquals(BookingStatus.CONFIRMED, mockBooking.getStatus());
	}

	@Test
	@DisplayName("결제 승인 실패 테스트 - 금액 불일치")
	void 결제_승인_실패_금액_불일치() {
		// GIVEN
		String orderId = "test-order-id";
		BigDecimal dbAmount = new BigDecimal("55000");
		BigDecimal requestAmount = new BigDecimal("1000");

		PaymentConfirmRequest confirmRequest = PaymentConfirmRequest.builder()
			.orderId(orderId)
			.paymentKey("test-payment-key")
			.amount(requestAmount)
			.build();

		Booking mockBooking = Booking.builder().bookingId(1L).status(BookingStatus.PENDING_PAYMENT).build();
		Payment mockPayment = Payment.builder().booking(mockBooking).orderId(orderId).amount(dbAmount).build();

		when(paymentRepository.findByOrderId(orderId)).thenReturn(Optional.of(mockPayment));

		// WHEN & THEN
		IllegalArgumentException exception = assertThrows(IllegalArgumentException.class, () -> {
			paymentService.confirmPayment(confirmRequest);
		});

		assertEquals("결제 금액이 일치하지 않습니다.", exception.getMessage());
		verify(webClient, never()).post();
		assertEquals(PaymentStatus.PENDING, mockPayment.getStatus());
		assertEquals(BookingStatus.PENDING_PAYMENT, mockBooking.getStatus());
	}
}
