package com.team03.ticketmon.concert.service;

import java.time.LocalDateTime;
import java.util.List;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import com.team03.ticketmon.concert.domain.Concert;
import com.team03.ticketmon.concert.dto.AiBatchSummaryResultDTO;
import com.team03.ticketmon.concert.dto.AiSummaryUpdateConditionDTO;
import com.team03.ticketmon.concert.dto.ReviewChangeDetectionDTO;
import com.team03.ticketmon.concert.repository.ConcertRepository;

@Service
public class AiBatchSummaryService {

	@Autowired
	private ConcertRepository concertRepository;

	@Autowired
	private AiSummaryUpdateConditionService conditionService;

	@Scheduled(cron = "0 0 2 * * *")
	public AiBatchSummaryResultDTO processBatch() {
		// 1단계: 사전 필터링
		AiSummaryUpdateConditionDTO condition = getUpdateCondition();
		List<Concert> candidateConcerts = concertRepository.findConcertsWithMinimumReviews(condition.getMinReviewCount());

		// 2단계: 후보군 정밀 검사
		int successCount = 0;
		int failCount = 0;

		for (Concert concert : candidateConcerts) {
			try {
				// 조건 체크
				ReviewChangeDetectionDTO detection = conditionService.checkNeedsUpdate(concert, condition);

				if (detection.getNeedsUpdate()) {
					// AI 처리 실행
					processConcertAiSummary(concert);
					successCount++;
				} else {
					// 스킵 (로그 기록)
					System.out.println("스킵: " + concert.getConcertId() + " - " + detection.getChangeReason());
				}
			} catch (Exception e) {
				failCount++;
				System.err.println("처리 실패: " + concert.getConcertId() + " - " + e.getMessage());
			}
		}

		return new AiBatchSummaryResultDTO(
			candidateConcerts.size(), successCount, failCount, LocalDateTime.now());
	}

	private AiSummaryUpdateConditionDTO getUpdateCondition() {
		return AiSummaryUpdateConditionDTO.builder()
			.minReviewCount(10)  // 통합됨!
			.significantCountChange(3)
			.significantCountChangeRatio(0.2)
			.updateOnAnyContentChange(true)
			.maxUpdateIntervalHours(168L)
			.build();
	}

	private void processConcertAiSummary(Concert concert) {
		// AI 요약 처리 로직
		System.out.println("AI 처리: " + concert.getConcertId());
	}
}