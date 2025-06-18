package com.team03.ticketmon.payment.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@NoArgsConstructor
public class PaymentCancelRequest {
	@NotBlank(message = "취소 사유는 필수입니다.")
	private String cancelReason;
}
