package com.team03.ticketmon.concert.controller;

import com.team03.ticketmon.concert.service.AiBatchSummaryService;
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

		// 🎯 엔티티 직접 조회 (새로 추가한 메서드 사용)
		var concert = concertService.getConcertEntityById(concertId)
			.orElseThrow(() -> new BusinessException(
				ErrorCode.CONCERT_NOT_FOUND,
				"콘서트를 찾을 수 없습니다."
			));

		// AI 요약 생성 처리
		batchSummaryService.processConcertAiSummary(concert);

		// 생성된 요약 조회
		String regeneratedSummary = concertService.getAiSummary(concertId);

		log.info("[ADMIN] 콘서트 AI 요약 재생성 완료 - concertId: {}", concertId);

		return ResponseEntity.ok(
			SuccessResponse.of("AI 요약이 생성되었습니다.", regeneratedSummary)
		);
	}
}
