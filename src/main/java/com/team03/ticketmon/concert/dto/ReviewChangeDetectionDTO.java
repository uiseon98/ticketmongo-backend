package com.team03.ticketmon.concert.dto;

import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.AllArgsConstructor;
import lombok.Builder;
import java.time.LocalDateTime;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ReviewChangeDetectionDTO {
	private Long concertId;
	private Integer currentReviewCount;
	private Integer lastSummaryReviewCount;
	private String currentReviewChecksum;
	private String lastSummaryChecksum;
	private LocalDateTime lastReviewModifiedAt;
	private LocalDateTime aiSummaryGeneratedAt;
	private Boolean needsUpdate;
	private String changeReason; // "COUNT_CHANGED", "CONTENT_CHANGED", "NEW_REVIEWS_ADDED"
}