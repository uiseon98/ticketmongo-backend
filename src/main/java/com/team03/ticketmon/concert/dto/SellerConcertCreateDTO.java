package com.team03.ticketmon.concert.dto;

import jakarta.validation.constraints.*;
import com.fasterxml.jackson.annotation.JsonIgnore;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.AllArgsConstructor;
import lombok.Builder;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.LocalDateTime;

import com.team03.ticketmon.concert.validation.ValidConcertTimes;

/*
 * Seller Concert Create DTO
 * 판매자용 콘서트 생성 전송 객체
 */

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
@ValidConcertTimes
@Schema(description = "콘서트 생성 정보")
public class SellerConcertCreateDTO {

	@NotBlank(message = "콘서트 제목은 필수입니다")
	@Size(max = 100, message = "콘서트 제목은 100자 이하여야 합니다")
	@Schema(description = "콘서트 제목", example = "아이유 콘서트 2025 'HEREH WORLD TOUR'", required = true, maxLength = 100)
	private String title;

	@NotBlank(message = "아티스트명은 필수입니다")
	@Size(max = 50, message = "아티스트명은 50자 이하여야 합니다")
	@Schema(description = "아티스트명", example = "아이유", required = true, maxLength = 50)
	private String artist;

	@Size(max = 1000, message = "콘서트 설명은 1000자 이하여야 합니다")
	@Schema(description = "콘서트 설명", example = "아이유의 2025년 새 앨범 발매 기념 월드투어 서울 공연", maxLength = 1000)
	private String description;

	@NotBlank(message = "공연장명은 필수입니다")
	@Size(max = 100, message = "공연장명은 100자 이하여야 합니다")
	@Schema(description = "공연장명", example = "올림픽공원 체조경기장", required = true, maxLength = 100)
	private String venueName;

	@Size(max = 200, message = "공연장 주소는 200자 이하여야 합니다")
	@Schema(description = "공연장 주소", example = "서울특별시 송파구 올림픽로 424", maxLength = 200)
	private String venueAddress;

	@NotNull(message = "콘서트 날짜는 필수입니다")
	@Future(message = "콘서트 날짜는 현재 날짜보다 이후여야 합니다")
	@Schema(description = "콘서트 날짜", example = "2025-08-15", required = true)
	private LocalDate concertDate;

	@NotNull(message = "시작 시간은 필수입니다")
	@Schema(description = "공연 시작 시간", example = "19:00:00", required = true)
	private LocalTime startTime;

	@NotNull(message = "종료 시간은 필수입니다")
	@Schema(description = "공연 종료 시간", example = "21:30:00", required = true)
	private LocalTime endTime;

	@NotNull(message = "총 좌석 수는 필수입니다")
	@Positive(message = "총 좌석 수는 양수여야 합니다")
	@Max(value = 100000, message = "총 좌석 수는 100,000석 이하여야 합니다")
	@Schema(description = "총 좌석 수", example = "8000", required = true, minimum = "1", maximum = "100000")
	private Integer totalSeats;

	@NotNull(message = "예매 시작일시는 필수입니다")
	@Future(message = "예매 시작일시는 현재 시간보다 이후여야 합니다")
	@Schema(description = "예매 시작일시", example = "2025-07-01T10:00:00", required = true)
	private LocalDateTime bookingStartDate;

	@NotNull(message = "예매 종료일시는 필수입니다")
	@Future(message = "예매 종료일시는 현재 시간보다 이후여야 합니다")
	@Schema(description = "예매 종료일시", example = "2025-08-14T23:59:59", required = true)
	private LocalDateTime bookingEndDate;

	@Min(value = 0, message = "최소 연령은 0세 이상이어야 합니다")
	@Max(value = 100, message = "최소 연령은 100세 이하여야 합니다")
	@Schema(description = "최소 연령 제한", example = "0", minimum = "0", maximum = "100")
	private Integer minAge;

	@Min(value = 1, message = "사용자당 최대 티켓 수는 1개 이상이어야 합니다")
	@Max(value = 10, message = "사용자당 최대 티켓 수는 10개 이하여야 합니다")
	@Schema(description = "사용자당 최대 구매 가능 티켓 수", example = "4", minimum = "1", maximum = "10")
	private Integer maxTicketsPerUser;

	@Pattern(regexp = "^https?://.*\\.(jpg|jpeg|png|gif|webp)$",
		message = "포스터 이미지 URL은 올바른 이미지 URL 형식이어야 합니다")
	@Schema(description = "포스터 이미지 URL", example = "https://example.com/posters/iu-2025.jpg")
	private String posterImageUrl;

	/**
	 * 공연 시간 순서 검증
	 */
	@JsonIgnore  // ← 이게 핵심!
	@AssertTrue(message = "종료 시간은 시작 시간보다 늦어야 합니다")
	public boolean isValidPerformanceTimes() {
		if (startTime == null || endTime == null) {
			return true; // @NotNull에서 처리
		}
		return endTime.isAfter(startTime);
	}

	/**
	 * 예매 시간 순서 검증
	 */
	@JsonIgnore  // ← 이것도!
	@AssertTrue(message = "예매 종료일시는 예매 시작일시보다 늦어야 합니다")
	public boolean isValidBookingTimes() {
		if (bookingStartDate == null || bookingEndDate == null) {
			return true; // @NotNull에서 처리
		}
		return bookingEndDate.isAfter(bookingStartDate);
	}

	/**
	 * 예매 기간과 공연 날짜 검증
	 */
	@JsonIgnore  // ← 이것도!
	@AssertTrue(message = "예매 종료일시는 공연 시작 전이어야 합니다")
	public boolean isValidBookingPeriod() {
		if (bookingEndDate == null || concertDate == null || startTime == null) {
			return true;
		}
		LocalDateTime concertStartDateTime = concertDate.atTime(startTime);
		return bookingEndDate.isBefore(concertStartDateTime);
	}
}