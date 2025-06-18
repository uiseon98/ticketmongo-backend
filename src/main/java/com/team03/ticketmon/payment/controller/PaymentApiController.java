package com.team03.ticketmon.payment.controller;

import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.reactive.result.view.RedirectView;
import org.springframework.web.util.UriUtils;

import com.team03.ticketmon.payment.dto.PaymentConfirmRequest;
import com.team03.ticketmon.payment.dto.PaymentExecutionResponse;
import com.team03.ticketmon.payment.dto.PaymentRequest;
import com.team03.ticketmon.payment.service.PaymentService;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@RestController
@RequiredArgsConstructor
@RequestMapping("/api/v1/payments")
public class PaymentApiController {

	private final PaymentService paymentService;

	@PostMapping("/request")
	public ResponseEntity<PaymentExecutionResponse> requestPayment(
		@Valid @RequestBody PaymentRequest paymentRequest) {

		PaymentExecutionResponse response = paymentService.initiatePayment(paymentRequest);
		return ResponseEntity.ok(response);
	}

	@GetMapping("/success")
	public RedirectView handlePaymentSuccess(
		@RequestParam String paymentKey,
		@RequestParam String orderId,
		@RequestParam BigDecimal amount) {

		log.info("결제 성공 리다이렉트 수신: paymentKey={}, orderId={}", paymentKey, orderId);

		try {
			PaymentConfirmRequest confirmRequest = PaymentConfirmRequest.builder()
				.paymentKey(paymentKey)
				.orderId(orderId)
				.amount(amount)
				.build();

			paymentService.confirmPayment(confirmRequest);

			// 성공 시 프론트엔드의 최종 성공 페이지로 리다이렉트
			return new RedirectView("/payment/result/success?orderId=" + orderId);

		} catch (Exception e) {
			log.error("결제 승인 처리 중 오류 발생: orderId={}, error={}", orderId, e.getMessage());
			// 실패 시 프론트엔드의 최종 실패 페이지로 리다이렉트
			String encodedMessage = UriUtils.encode(e.getMessage(), StandardCharsets.UTF_8);
			return new RedirectView("/payment/result/fail?orderId=" + orderId + "&message=" + encodedMessage);
		}
	}
}
