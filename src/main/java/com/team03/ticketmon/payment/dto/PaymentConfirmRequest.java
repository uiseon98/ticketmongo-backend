package com.team03.ticketmon.payment.dto;

import java.math.BigDecimal;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Builder;
import lombok.Getter;

@Getter
@Builder
public class PaymentConfirmRequest {
	@NotBlank
	private String paymentKey;
	@NotBlank
	private String orderId;
	@NotNull
	private BigDecimal amount;
}
