package com.team03.ticketmon.concert.dto;

import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.AllArgsConstructor;
import lombok.Builder;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class AiSummaryUpdateConditionDTO {
	private Integer minReviewCount = 10;              // 통합됨!
	private Integer significantCountChange = 3;       // 유지
	private Double significantCountChangeRatio = 0.2; // 유지
	private Boolean updateOnAnyContentChange = true;  // 유지
	private Long maxUpdateIntervalHours = 168L;       // 24L → 168L (1주일)
}