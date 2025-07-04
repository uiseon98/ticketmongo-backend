package com.team03.ticketmon.concert.dto;

import jakarta.validation.constraints.*;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.AllArgsConstructor;

/*
 * Seller Concert Image Update DTO
 * 판매자용 콘서트 이미지 수정 전송 객체
 */

@Data
@NoArgsConstructor
@AllArgsConstructor
public class SellerConcertImageUpdateDTO {

	@NotBlank(message = "포스터 이미지 URL은 필수입니다")
	@Size(max = 2000, message = "URL이 너무 깁니다")
	private String posterImageUrl;
}
