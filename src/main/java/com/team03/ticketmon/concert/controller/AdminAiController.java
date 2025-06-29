package com.team03.ticketmon.concert.controller;

import java.time.LocalDateTime;
import java.util.List;

import com.team03.ticketmon._global.config.AiSummaryConditionProperties;
import com.team03.ticketmon.concert.domain.Concert;
import com.team03.ticketmon.concert.domain.Review;
import com.team03.ticketmon.concert.dto.ReviewChangeDetectionDTO;
import com.team03.ticketmon.concert.repository.ConcertRepository;
import com.team03.ticketmon.concert.repository.ReviewRepository;
import com.team03.ticketmon.concert.service.AiBatchSummaryService;
import com.team03.ticketmon.concert.service.AiSummaryUpdateConditionService;
import com.team03.ticketmon.concert.service.ConcertService;
import com.team03.ticketmon._global.exception.BusinessException;
import com.team03.ticketmon._global.exception.ErrorCode;
import com.team03.ticketmon._global.exception.SuccessResponse;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.ExampleObject;

import jakarta.validation.constraints.Min;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

import org.springframework.http.ResponseEntity;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

/**
 * 🛠️ Admin AI Controller
 * 관리자 전용 AI 요약 관리 API
 */
@Tag(name = "관리자 AI API", description = "관리자 전용 AI 요약 관리 기능")
@RestController
@RequestMapping("/api/admin/ai")
@RequiredArgsConstructor
@Validated
@Slf4j
public class AdminAiController {

	private final AiBatchSummaryService batchSummaryService;
	private final ConcertService concertService;
	private final AiSummaryUpdateConditionService conditionService;
	private final AiSummaryConditionProperties conditionProperties;
	private final ReviewRepository reviewRepository;
	private final ConcertRepository concertRepository;

	@Operation(
		summary = "콘서트 AI 요약 수동 재생성",
		description = """
        관리자가 특정 콘서트의 AI 요약을 수동으로 재생성합니다.
        
        📋 **동작 조건**:
        - 배치 처리와 동일한 조건 적용 (10자 이상 리뷰만 유효)
        - 조건 미충족 시 명확한 에러 메시지 제공
        
        ⚠️ **주의사항**:
        - 리뷰가 부족하거나 조건을 만족하지 않으면 실패합니다
        - AI 서비스 장애 시 재시도가 필요할 수 있습니다
        """
	)
	@ApiResponses({
		@ApiResponse(
			responseCode = "200",
			description = "AI 요약 재생성 성공",
			content = @Content(
				mediaType = "application/json",
				examples = @ExampleObject(
					name = "성공 응답 예시",
					value = """
                    {
                        "success": true,
                        "message": "AI 요약이 생성되었습니다.",
                        "data": "아이유의 2025년 새 앨범 발매 기념 월드투어 서울 공연으로, 신곡과 대표곡을 함께 들을 수 있는 특별한 무대입니다. 관객들의 후기에 따르면 라이브 실력과 무대 연출이 매우 인상적이었다고 평가받고 있습니다."
                    }
                    """
				)
			)
		),
		@ApiResponse(
			responseCode = "400",
			description = "잘못된 요청 또는 조건 미충족",
			content = @Content(
				examples = {
					@ExampleObject(
						name = "리뷰 부족",
						value = """
                        {
                            "success": false,
                            "message": "이 콘서트에는 아직 리뷰가 없어서 AI 요약을 생성할 수 없습니다. 리뷰가 작성된 후 다시 시도해주세요.",
                            "data": null
                        }
                        """
					),
					@ExampleObject(
						name = "리뷰 내용 부족",
						value = """
                        {
                            "success": false,
                            "message": "리뷰 내용이 너무 짧아서 AI 요약을 생성할 수 없습니다. 최소 10자 이상의 리뷰가 필요합니다.",
                            "data": null
                        }
                        """
					)
				}
			)
		),
		@ApiResponse(responseCode = "404", description = "콘서트를 찾을 수 없음"),
		@ApiResponse(responseCode = "500", description = "AI 서비스 오류")
	})
	@PostMapping("/concerts/{concertId}/summary/regenerate")
	public ResponseEntity<SuccessResponse<String>> regenerateAiSummary(
		@Parameter(
			description = "**콘서트 ID** (1 이상의 양수)",
			example = "1"
		)
		@PathVariable @Min(1) Long concertId) {

		log.info("[ADMIN] 콘서트 AI 요약 수동 재생성 시작 - concertId: {}", concertId);

		// 🎯 엔티티 직접 조회
		var concert = concertService.getConcertEntityById(concertId)
			.orElseThrow(() -> new BusinessException(
				ErrorCode.CONCERT_NOT_FOUND,
				"콘서트를 찾을 수 없습니다."
			));

		try {
			// ✅ 최소 리뷰 개수 검증 추가
			List<Review> validReviews = reviewRepository.findValidReviewsForAiSummary(concertId);

			if (validReviews.size() < conditionProperties.getMinReviewCount()) {
				// 조건 미충족 시에도 실패 정보 기록
				recordAiSummaryFailure(concert, "INSUFFICIENT_REVIEWS",
					String.format("최소 %d개의 유효한 리뷰가 필요합니다. 현재: %d개",
						conditionProperties.getMinReviewCount(), validReviews.size()));

				throw new BusinessException(ErrorCode.AI_SUMMARY_CONDITION_NOT_MET,
					String.format("AI 요약 생성을 위해서는 최소 %d개의 유효한 리뷰가 필요합니다. 현재: %d개",
						conditionProperties.getMinReviewCount(), validReviews.size()));
			}

			// 기존 조건 검증 (업데이트 필요성)
			ReviewChangeDetectionDTO detection = conditionService.checkNeedsUpdate(concert, conditionProperties);

			if (!detection.getNeedsUpdate()) {
				// 조건 미충족 시에도 실패 정보 기록
				recordAiSummaryFailure(concert, "CONDITION_NOT_MET", detection.getChangeReason());

				throw new BusinessException(ErrorCode.AI_SUMMARY_CONDITION_NOT_MET,
					"AI 요약 생성 조건을 만족하지 않습니다: " + detection.getChangeReason());
			}

			// AI 요약 생성 처리
			batchSummaryService.processConcertAiSummary(concert);

			// 생성된 요약 조회
			String regeneratedSummary = concertService.getAiSummary(concertId);

			log.info("[ADMIN] 콘서트 AI 요약 재생성 완료 - concertId: {}", concertId);

			return ResponseEntity.ok(
				SuccessResponse.of("AI 요약이 생성되었습니다.", regeneratedSummary)
			);

		} catch (BusinessException e) {
			// 이미 recordAiSummaryFailure가 호출된 경우는 제외하고 처리
			if (!e.getErrorCode().equals(ErrorCode.AI_SUMMARY_CONDITION_NOT_MET)) {
				recordAiSummaryFailure(concert, "BUSINESS_ERROR", e.getMessage());
			}

			log.error("[ADMIN] AI 요약 생성 실패 - concertId: {}, 에러: {}", concertId, e.getMessage());
			throw e;

		} catch (Exception e) {
			recordAiSummaryFailure(concert, "SYSTEM_ERROR", e.getMessage());

			log.error("[ADMIN] AI 요약 생성 중 예상치 못한 오류 - concertId: {}", concertId, e);
			throw new BusinessException(ErrorCode.SERVER_ERROR,
				"AI 요약 생성 중 시스템 오류가 발생했습니다. 잠시 후 다시 시도해주세요.");
		}
	}

	/**
	 * AI 요약 실패 정보를 기록하는 헬퍼 메서드
	 */
	private void recordAiSummaryFailure(Concert concert, String failureType, String failureReason) {
		try {
			LocalDateTime now = LocalDateTime.now();

			// 실패 카운터 증가
			Integer currentRetryCount = concert.getAiSummaryRetryCount();
			int newRetryCount = (currentRetryCount != null ? currentRetryCount : 0) + 1;
			concert.setAiSummaryRetryCount(newRetryCount);

			// 실패 시간 기록
			concert.setAiSummaryLastFailedAt(now);

			// 데이터베이스에 실패 정보 저장
			concertRepository.save(concert);

			log.info("[ADMIN] AI 요약 실패 정보 저장 완료: concertId={}, 실패유형={}, 재시도횟수={}, 실패시간={}",
				concert.getConcertId(), failureType, newRetryCount, now);

		} catch (Exception saveException) {
			log.error("[ADMIN] AI 요약 실패 정보 저장 중 오류 발생: concertId={}",
				concert.getConcertId(), saveException);
		}
	}
}
