package com.team03.ticketmon.payment.controller;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import com.team03.ticketmon.payment.domain.dto.PaymentExecutionResponse;
import com.team03.ticketmon.payment.domain.dto.PaymentRequest;
import com.team03.ticketmon.payment.service.PaymentService;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;

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
}
