package com.team03.ticketmon.payment.controller;

import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.stream.Collectors;

import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseBody;
import org.springframework.web.util.UriUtils;

import com.team03.ticketmon.booking.domain.Booking;
import com.team03.ticketmon.booking.domain.BookingStatus;
import com.team03.ticketmon.booking.repository.BookingRepository;
import com.team03.ticketmon.payment.dto.BookingResponseDto;
import com.team03.ticketmon.payment.dto.PaymentCancelRequest;
import com.team03.ticketmon.payment.dto.PaymentConfirmRequest;
import com.team03.ticketmon.payment.dto.PaymentExecutionResponse;
import com.team03.ticketmon.payment.dto.PaymentHistoryDto;
import com.team03.ticketmon.payment.dto.PaymentRequest;
import com.team03.ticketmon.payment.service.PaymentService;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Slf4j // 로그 출력을 위한 Lombok 어노테이션
@Controller // JSON과 Redirect(페이지 이동) 모두 지원하기 위해 @Controller 사용
@RequiredArgsConstructor // 생성자 주입 자동 생성
@RequestMapping("/api/v1/payments") // 모든 메서드의 기본 URL 경로
public class PaymentApiController {

	private final PaymentService paymentService; // 결제 관련 비즈니스 로직을 처리하는 서비스
	private final BookingRepository bookingRepository;

	/**
	 * 결제 요청 API
	 * - 클라이언트에서 결제 요청 정보를 받아 결제 준비 정보를 반환
	 * - JSON 요청/응답
	 */
	@PostMapping("/request")
	@ResponseBody // JSON 형태로 응답
	public ResponseEntity<PaymentExecutionResponse> requestPayment(
		@Valid @RequestBody PaymentRequest paymentRequest) { // 결제 요청 정보(예매번호 등) JSON으로 받음

		// 💡 [수정] 기존 bookingNumber를 사용하는 로직 대신, 새로운 DTO를 사용하도록 변경
		// 예시: bookingNumber를 사용하여 paymentService를 호출하는 로직으로 변경 필요
		// PaymentExecutionResponse response = paymentService.initiatePayment(paymentRequest.getBookingNumber());

		// 임시로 기존 로직을 유지하되, 실제로는 paymentRequest 객체를 활용해야 합니다.
		// 결제 준비(결제 정보 생성 또는 재사용)
		PaymentExecutionResponse response = paymentService.initiatePayment(paymentRequest);
		// 200 OK + 결제 준비 정보 반환
		return ResponseEntity.ok(response);
	}

	/**
	 * 결제 성공 콜백(리다이렉트)
	 * - 결제 성공 시 PG사에서 호출
	 * - 결제 승인 처리 후, 성공 결과 페이지로 리다이렉트
	 */
	@GetMapping("/success")
	public String handlePaymentSuccess(
		@RequestParam String paymentKey, // PG사에서 전달하는 결제키
		@RequestParam String orderId,    // 주문 ID
		@RequestParam BigDecimal amount) { // 결제 금액

		log.info("결제 성공 리다이렉트 수신: paymentKey={}, orderId={}", paymentKey, orderId);

		try {
			// 결제 승인 요청 객체 생성
			PaymentConfirmRequest confirmRequest = PaymentConfirmRequest.builder()
				.paymentKey(paymentKey)
				.orderId(orderId)
				.amount(amount)
				.build();

			// 결제 승인 처리(실제 결제 확정)
			paymentService.confirmPayment(confirmRequest);

			// 결제 성공 결과 페이지로 리다이렉트
			// 💡 [핵심 수정] React 앱의 성공 페이지 주소로 리다이렉트합니다.
			// TODO: 실제 운영 환경에서는 이 주소를 application.yml 등에서 관리해야 합니다.
			String reactSuccessUrl = "http://localhost:3000/payment/result/success";
			return "redirect:" + reactSuccessUrl + "?orderId=" + orderId;

		} catch (Exception e) {
			log.error("결제 승인 처리 중 오류 발생: orderId={}, error={}", orderId, e.getMessage());
			String encodedMessage = UriUtils.encode(e.getMessage(), StandardCharsets.UTF_8);

			// 💡 [핵심 수정] React 앱의 실패 페이지 주소로 리다이렉트합니다.
			String reactFailUrl = "http://localhost:3000/payment/result/fail";
			return "redirect:" + reactFailUrl + "?orderId=" + orderId + "&message=" + encodedMessage;
		}
	}

	/**
	 * 결제 실패 콜백(리다이렉트)
	 * - 결제 실패 시 PG사에서 호출
	 * - 결제 실패 처리 후, 실패 결과 페이지로 리다이렉트
	 */
	@GetMapping("/fail")
	public String handlePaymentFail(
		@RequestParam String code,    // 실패 코드
		@RequestParam String message, // 실패 메시지
		@RequestParam String orderId) { // 주문 ID

		log.warn("결제 실패 리다이렉트 수신: orderId={}, code={}, message={}", orderId, code, message);
		// 결제 실패 처리(상태 변경 등)
		paymentService.handlePaymentFailure(orderId, code, message);

		String encodedMessage = UriUtils.encode(message, StandardCharsets.UTF_8);
		// 결제 실패 결과 페이지로 리다이렉트
		String reactFailUrl = "http://localhost:3000/payment/result/fail";
		return "redirect:" + reactFailUrl + "?orderId=" + orderId + "&code=" + code + "&message=" + encodedMessage;
	}

	/**
	 * 결제 취소 요청 API
	 * - 사용자가 결제 취소를 요청할 때 사용
	 * - JSON 요청/응답
	 */
	@PostMapping("/{orderId}/cancel")
	@ResponseBody // JSON 형태로 응답
	public ResponseEntity<Void> cancelPayment(
		@PathVariable String orderId, // 취소할 주문 ID
		@Valid @RequestBody PaymentCancelRequest cancelRequest) { // 취소 사유 등

		// 결제 취소 처리
		paymentService.cancelPayment(orderId, cancelRequest);
		// 200 OK 반환(본문 없음)
		return ResponseEntity.ok().build();
	}

	/**
	 * 결제 내역 조회 API
	 * - 현재 로그인된 사용자의 결제 내역을 조회
	 * - (실제 서비스에서는 인증을 통해 사용자 ID를 동적으로 가져와야 함)
	 */
	@GetMapping("/history")
	@ResponseBody // JSON 형태로 응답
	public ResponseEntity<List<PaymentHistoryDto>> getPaymentHistory() {
		// TODO: 실제로는 인증 정보를 통해 사용자 ID를 가져와야 함
		Long currentUserId = 1L; // (임시) 사용자 ID 하드코딩
		// 결제 내역 조회
		List<PaymentHistoryDto> history = paymentService.getPaymentHistoryByUserId(currentUserId);
		// 200 OK + 결제 내역 리스트 반환
		return ResponseEntity.ok(history);
	}

	/**
	 * 결제 대기중인 예매 목록을 조회하는 API (React 연동용)
	 */
	@GetMapping("/pending-bookings")
	@ResponseBody
	public ResponseEntity<List<BookingResponseDto>> getPendingBookings() {
		// TODO: 실제로는 현재 로그인한 사용자의 예매 목록만 가져와야 합니다.
		// Long currentUserId = ... ;
		// List<Booking> pendingBookings = bookingRepository.findByUserIdAndStatus(currentUserId, BookingStatus.PENDING_PAYMENT);

		// 현재는 테스트를 위해 모든 PENDING_PAYMENT 상태의 예매를 가져옵니다.
		List<Booking> pendingBookings = bookingRepository.findByStatus(BookingStatus.PENDING_PAYMENT);

		List<BookingResponseDto> dtos = pendingBookings.stream()
			.map(BookingResponseDto::new)
			.collect(Collectors.toList());

		return ResponseEntity.ok(dtos);
	}
}

/*
 * (참고) 실제 인증 적용 예시
 *
 * @GetMapping("/history")
 * @ResponseBody
 * public ResponseEntity<List<PaymentHistoryDto>> getPaymentHistory(
 *    @AuthenticationPrincipal CustomUserDetails userDetails) { // 로그인 사용자 정보 주입
 *
 *    if (userDetails == null) {
 *       // 비로그인 시 401 Unauthorized
 *       return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
 *    }
 *    Long currentUserId = userDetails.getUserId();
 *    List<PaymentHistoryDto> history = paymentService.getPaymentHistoryByUserId(currentUserId);
 *    return ResponseEntity.ok(history);
 * }
 */
