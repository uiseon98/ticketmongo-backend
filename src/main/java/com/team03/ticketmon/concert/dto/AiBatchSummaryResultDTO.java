package com.team03.ticketmon.concert.dto;

import java.time.LocalDateTime;

/**
 * AI 배치 요약 처리 결과를 담는 DTO
 */
public class AiBatchSummaryResultDTO {
	private int totalProcessed;      // 총 처리된 콘서트 수
	private int successCount;        // 성공한 처리 수
	private int failCount;           // 실패한 처리 수
	private LocalDateTime processedAt; // 처리 완료 시간

	// 기본 생성자
	public AiBatchSummaryResultDTO() {}

	// 전체 생성자
	public AiBatchSummaryResultDTO(int totalProcessed, int successCount,
		int failCount, LocalDateTime processedAt) {
		this.totalProcessed = totalProcessed;
		this.successCount = successCount;
		this.failCount = failCount;
		this.processedAt = processedAt;
	}

	// Getter & Setter
	public int getTotalProcessed() {
		return totalProcessed;
	}

	public void setTotalProcessed(int totalProcessed) {
		this.totalProcessed = totalProcessed;
	}

	public int getSuccessCount() {
		return successCount;
	}

	public void setSuccessCount(int successCount) {
		this.successCount = successCount;
	}

	public int getFailCount() {
		return failCount;
	}

	public void setFailCount(int failCount) {
		this.failCount = failCount;
	}

	public LocalDateTime getProcessedAt() {
		return processedAt;
	}

	public void setProcessedAt(LocalDateTime processedAt) {
		this.processedAt = processedAt;
	}

	// 처리 성공률을 계산하는 메서드
	public double getSuccessRate() {
		if (totalProcessed == 0) return 0.0;
		return (double) successCount / totalProcessed * 100.0;
	}

	@Override
	public String toString() {
		return String.format(
			"AiBatchSummaryResultDTO{totalProcessed=%d, successCount=%d, failCount=%d, processedAt=%s, successRate=%.2f%%}",
			totalProcessed, successCount, failCount, processedAt, getSuccessRate()
		);
	}
}