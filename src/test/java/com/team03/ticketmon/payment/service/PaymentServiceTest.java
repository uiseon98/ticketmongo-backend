package com.team03.ticketmon.payment.service;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import static org.mockito.Mockito.anyMap;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Spy;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.web.reactive.function.client.WebClient;

import com.team03.ticketmon.concert.domain.Booking;
import com.team03.ticketmon.concert.domain.Concert;
import com.team03.ticketmon.concert.domain.enums.BookingStatus;
import com.team03.ticketmon.concert.repository.BookingRepository;
import com.team03.ticketmon.payment.config.TossPaymentsProperties;
import com.team03.ticketmon.payment.domain.entity.Payment;
import com.team03.ticketmon.payment.domain.entity.PaymentCancelHistory;
import com.team03.ticketmon.payment.domain.enums.PaymentStatus;
import com.team03.ticketmon.payment.dto.PaymentCancelRequest;
import com.team03.ticketmon.payment.dto.PaymentConfirmRequest;
import com.team03.ticketmon.payment.repository.PaymentCancelHistoryRepository;
import com.team03.ticketmon.payment.repository.PaymentRepository;

import reactor.core.publisher.Mono;

@ExtendWith(MockitoExtension.class)
class PaymentServiceTest {

	@Spy
	@InjectMocks
	private PaymentService paymentService;

	@Mock
	private PaymentRepository paymentRepository;
	@Mock
	private BookingRepository bookingRepository;
	@Mock
	private PaymentCancelHistoryRepository paymentCancelHistoryRepository; // 💡 [추가]
	@Mock
	private TossPaymentsProperties tossPaymentsProperties;
	@Mock
	private WebClient webClient; // 💡 [추가] 취소 테스트에서 WebClient Mock이 다시 필요합니다.

	// 💡 [추가] WebClient Mocking을 위한 객체들
	@Mock
	private WebClient.RequestBodyUriSpec requestBodyUriSpec;
	@Mock
	private WebClient.RequestBodySpec requestBodySpec;
	@Mock(lenient = true)
	private WebClient.ResponseSpec responseSpec;

	// ... (기존의 결제_승인_성공/실패_테스트 메소드는 그대로 둡니다) ...

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

		Map<String, Object> tossResponse = Map.of(
			"paymentKey", paymentKey,
			"approvedAt", "2025-06-21T12:00:00+09:00"
		);

		doReturn(Mono.just(tossResponse)).when(paymentService).callTossConfirmApi(anyMap(), anyString());

		// WHEN
		paymentService.confirmPayment(confirmRequest);

		// THEN
		verify(paymentRepository, times(1)).findByOrderId(orderId);
		verify(paymentService, times(1)).callTossConfirmApi(anyMap(), anyString());
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
		verify(paymentService, never()).callTossConfirmApi(any(), any());
		assertEquals(PaymentStatus.PENDING, mockPayment.getStatus());
		assertEquals(BookingStatus.PENDING_PAYMENT, mockBooking.getStatus());
	}

	// =================================================================
	// 💡 [신규 테스트 추가 1] 결제 취소 성공 테스트
	// =================================================================
	@Test
	@DisplayName("결제 취소 성공 테스트")
	void 결제_취소_성공_테스트() {
		// GIVEN
		String orderId = "test-order-id-to-cancel";
		String paymentKey = "test-payment-key";

		// 💡 [필수 수정] ReflectionTestUtils를 사용하여 cancelReason 필드에 값을 설정합니다.
		PaymentCancelRequest cancelRequest = new PaymentCancelRequest();
		ReflectionTestUtils.setField(cancelRequest, "cancelReason", "테스트 취소");

		Booking mockBooking = spy(Booking.builder().bookingId(1L).build());
		Payment mockPayment = spy(new Payment(mockBooking, orderId, new BigDecimal("55000")));
		mockPayment.complete(paymentKey, LocalDateTime.now());

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

		// 토스페이먼츠 취소 응답 Mocking
		Map<String, Object> cancelDetails = Map.of(
			"transactionKey", "cancel-transaction-key",
			"cancelAmount", 55000,
			"canceledAt", "2025-06-21T13:00:00+09:00"
		);
		Map<String, Object> tossCancelResponse = Map.of("cancels", List.of(cancelDetails));
		when(responseSpec.bodyToMono(Map.class)).thenReturn(Mono.just(tossCancelResponse));

		// WHEN
		paymentService.cancelPayment(orderId, cancelRequest);

		// THEN
		verify(paymentRepository, times(1)).findByOrderId(orderId);
		verify(webClient.post(), times(1)).uri("https://api.tosspayments.com/v1/payments/" + paymentKey + "/cancel");
		verify(paymentCancelHistoryRepository, times(1)).save(any(PaymentCancelHistory.class));

		// 💡 spy 객체의 실제 메소드가 호출되었는지 검증
		verify(mockPayment, times(1)).cancel();
		verify(mockBooking, times(1)).cancel();

		assertEquals(PaymentStatus.CANCELED, mockPayment.getStatus());
		assertEquals(BookingStatus.CANCELED, mockBooking.getStatus());
	}

	// =================================================================
	// 💡 [신규 테스트 추가 2] 이미 취소된 결제에 대한 취소 실패 테스트
	// =================================================================
	@Test
	@DisplayName("결제 취소 실패 테스트 - 이미 취소된 결제")
	void 결제_취소_실패_이미_취소됨() {
		// GIVEN
		String orderId = "already-canceled-order-id";
		PaymentCancelRequest cancelRequest = new PaymentCancelRequest();

		Booking mockBooking = Booking.builder().bookingId(1L).build();
		Payment mockPayment = new Payment(mockBooking, orderId, new BigDecimal("55000"));
		mockPayment.cancel(); // 💡 이미 취소된 상태로 만듦

		when(paymentRepository.findByOrderId(orderId)).thenReturn(Optional.of(mockPayment));

		// WHEN & THEN
		IllegalStateException exception = assertThrows(IllegalStateException.class, () -> {
			paymentService.cancelPayment(orderId, cancelRequest);
		});

		assertEquals("이미 취소된 결제입니다.", exception.getMessage());
		verify(webClient, never()).post(); // WebClient는 절대 호출되지 않아야 함
	}
}
