package com.team03.ticketmon.concert.controller;

import com.team03.ticketmon._global.exception.BusinessException;
import com.team03.ticketmon._global.exception.ErrorCode;
import com.team03.ticketmon.concert.domain.Concert;
import com.team03.ticketmon.concert.domain.Review;
import com.team03.ticketmon.concert.dto.*;
import com.team03.ticketmon.concert.domain.enums.ConcertStatus;
import com.team03.ticketmon.concert.service.SellerConcertService;
import com.team03.ticketmon._global.exception.SuccessResponse;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.media.ExampleObject;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Min;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;
import java.util.List;
import java.util.Set;
import com.team03.ticketmon._global.config.AiSummaryConditionProperties;
import com.team03.ticketmon.concert.domain.Concert;
import com.team03.ticketmon.concert.repository.ConcertRepository;
import com.team03.ticketmon.concert.repository.ReviewRepository;
import com.team03.ticketmon.concert.service.AiBatchSummaryService;
import com.team03.ticketmon.concert.service.ConcertService;
import lombok.extern.slf4j.Slf4j;
import java.time.LocalDateTime;

/**
 * Seller Concert Controller
 * 판매자용 콘서트 관련 HTTP 요청 처리
 */
@Slf4j
@Tag(name = "판매자용 콘서트 API", description = "판매자용 콘서트 등록, 수정, 관리 관련 API")
@RestController
@RequestMapping("/api/seller/concerts")
@RequiredArgsConstructor
@Validated
public class SellerConcertController {

	private static final Set<String> ALLOWED_SORT_FIELDS = Set.of(
		"createdAt", "title", "concertDate", "artist", "status"
	);
	private final SellerConcertService sellerConcertService;
	private final AiBatchSummaryService batchSummaryService;
	private final ConcertService concertService;
	private final AiSummaryConditionProperties conditionProperties;
	private final ReviewRepository reviewRepository;
	private final ConcertRepository concertRepository;

	@Operation(
		summary = "판매자 콘서트 목록 조회",
		description = """
		특정 판매자의 콘서트 목록을 페이징으로 조회합니다.
		생성일시 기준 내림차순으로 정렬됩니다.
		"""
	)
	@ApiResponses({
		@ApiResponse(
			responseCode = "200",
			description = "판매자 콘서트 목록 조회 성공",
			content = @Content(
				mediaType = "application/json",
				schema = @Schema(implementation = SuccessResponse.class),
				examples = @ExampleObject(
					name = "성공 응답 예시",
					value = """
					{
						"success": true,
						"message": "성공",
						"data": {
							"content": [
								{
									"concertId": 1,
									"title": "아이유 콘서트 2025",
									"artist": "아이유",
									"sellerId": 100,
									"status": "ON_SALE",
									"venueName": "올림픽공원 체조경기장",
									"concertDate": "2025-08-15",
									"startTime": "19:00:00",
									"totalSeats": 8000,
									"createdAt": "2025-06-20T10:00:00",
									"updatedAt": "2025-06-21T15:30:00"
								}
							],
							"totalElements": 15,
							"totalPages": 2,
							"size": 10,
							"number": 0,
							"first": true,
							"last": false
						}
					}
					"""
				)
			)
		),
		@ApiResponse(
			responseCode = "400",
			description = "잘못된 판매자 ID",
			content = @Content(
				examples = @ExampleObject(
					value = """
					{
						"success": false,
						"message": "유효하지 않은 판매자 ID입니다",
						"data": null
					}
					"""
				)
			)
		)
	})

	@GetMapping
	public ResponseEntity<SuccessResponse<Page<SellerConcertDTO>>> getSellerConcerts(
		@Parameter(
			description = "**판매자 ID** (1 이상의 양수)",
			example = "100",
			schema = @Schema(minimum = "1")
		)
		@RequestParam @Min(1) Long sellerId,

		@Parameter(
			description = "**페이지 번호** (0부터 시작)",
			example = "0",
			schema = @Schema(minimum = "0")
		)
		@RequestParam(defaultValue = "0") int page,

		@Parameter(
			description = "**페이지 크기** (한 페이지당 항목 수)",
			example = "10",
			schema = @Schema(minimum = "1", maximum = "100")
		)
		@RequestParam(defaultValue = "10") int size,

		@Parameter(
			description = "**정렬 기준** (createdAt, title, concertDate, artist 등)",
			example = "createdAt",
			schema = @Schema(allowableValues = {"createdAt", "title", "concertDate", "artist", "status"})
		)
		@RequestParam(defaultValue = "createdAt") String sortBy,

		@Parameter(
			description = "**정렬 방향** (asc: 오름차순, desc: 내림차순)",
			example = "desc",
			schema = @Schema(allowableValues = {"asc", "desc"})
		)
		@RequestParam(defaultValue = "desc") String sortDir
	) {

		// sortBy 필드 검증
		if (!ALLOWED_SORT_FIELDS.contains(sortBy)) {
			throw new IllegalArgumentException("허용되지 않은 정렬 필드입니다: " + sortBy);
		}

		// 안전한 Sort 객체 생성
		Sort.Direction direction = "asc".equalsIgnoreCase(sortDir)
			? Sort.Direction.ASC
			: Sort.Direction.DESC;

		Sort sort = Sort.by(direction, sortBy);
		Pageable pageable = PageRequest.of(page, size, sort);

		Page<SellerConcertDTO> concerts = sellerConcertService.getSellerConcerts(sellerId, pageable);
		return ResponseEntity.ok(SuccessResponse.of(concerts));
	}

	@Operation(
		summary = "판매자별 상태별 콘서트 조회",
		description = """
		특정 판매자의 특정 상태 콘서트들을 조회합니다.
		콘서트 날짜 기준 오름차순으로 정렬됩니다.
		"""
	)
	@ApiResponses({
		@ApiResponse(
			responseCode = "200",
			description = "상태별 콘서트 조회 성공",
			content = @Content(
				mediaType = "application/json",
				examples = @ExampleObject(
					name = "ON_SALE 상태 콘서트 목록",
					value = """
					{
						"success": true,
						"message": "성공",
						"data": [
							{
								"concertId": 1,
								"title": "아이유 콘서트 2025",
								"artist": "아이유",
								"sellerId": 100,
								"status": "ON_SALE",
								"concertDate": "2025-08-15",
								"venueName": "올림픽공원 체조경기장"
							},
							{
								"concertId": 2,
								"title": "BTS 월드투어 서울",
								"artist": "BTS",
								"sellerId": 100,
								"status": "ON_SALE",
								"concertDate": "2025-09-20",
								"venueName": "잠실올림픽주경기장"
							}
						]
					}
					"""
				)
			)
		),
		@ApiResponse(responseCode = "400", description = "잘못된 판매자 ID 또는 상태값")
	})
	@GetMapping("/status")
	public ResponseEntity<SuccessResponse<List<SellerConcertDTO>>> getSellerConcertsByStatus(
		@Parameter(
			description = "**판매자 ID** (1 이상의 양수)",
			example = "100",
			schema = @Schema(minimum = "1")
		)
		@RequestParam @Min(1) Long sellerId,

		@Parameter(
			description = """
			**콘서트 상태**
			- SCHEDULED: 예정됨 (예매 시작 전)
			- ON_SALE: 예매 중 (현재 구매 가능)
			- SOLD_OUT: 매진됨
			- CANCELLED: 취소됨
			- COMPLETED: 완료됨
			""",
			example = "ON_SALE",
			schema = @Schema(allowableValues = {"SCHEDULED", "ON_SALE", "SOLD_OUT", "CANCELLED", "COMPLETED"})
		)
		@RequestParam ConcertStatus status) {

		List<SellerConcertDTO> concerts = sellerConcertService
			.getSellerConcertsByStatus(sellerId, status);

		return ResponseEntity.ok(SuccessResponse.of(concerts));
	}

	@Operation(
		summary = "콘서트 생성",
		description = """
		새로운 콘서트를 등록합니다.
		자동으로 SCHEDULED 상태로 생성되며, 모든 시간 관련 유효성 검증이 수행됩니다.
		"""
	)
	@ApiResponses({
		@ApiResponse(
			responseCode = "201",
			description = "콘서트 생성 성공",
			content = @Content(
				mediaType = "application/json",
				examples = @ExampleObject(
					name = "생성 성공 응답",
					value = """
					{
						"success": true,
						"message": "콘서트가 생성되었습니다.",
						"data": {
							"concertId": 1,
							"title": "아이유 콘서트 2025 'HEREH WORLD TOUR'",
							"artist": "아이유",
							"description": "아이유의 2025년 새 앨범 발매 기념 월드투어 서울 공연",
							"sellerId": 100,
							"venueName": "올림픽공원 체조경기장",
							"venueAddress": "서울특별시 송파구 올림픽로 424",
							"concertDate": "2025-08-15",
							"startTime": "19:00:00",
							"endTime": "21:30:00",
							"totalSeats": 8000,
							"bookingStartDate": "2025-07-01T10:00:00",
							"bookingEndDate": "2025-08-14T23:59:59",
							"minAge": 0,
							"maxTicketsPerUser": 4,
							"status": "SCHEDULED",
							"posterImageUrl": "https://example.com/posters/iu-2025.jpg",
							"createdAt": "2025-06-22T10:00:00",
							"updatedAt": "2025-06-22T10:00:00"
						}
					}
					"""
				)
			)
		),
		@ApiResponse(
			responseCode = "400",
			description = "잘못된 입력값",
			content = @Content(
				examples = {
					@ExampleObject(
						name = "필수 필드 누락",
						value = """
						{
							"success": false,
							"message": "콘서트 제목은 필수입니다",
							"data": null
						}
						"""
					),
					@ExampleObject(
						name = "시간 검증 오류",
						value = """
						{
							"success": false,
							"message": "종료 시간은 시작 시간보다 늦어야 합니다",
							"data": null
						}
						"""
					)
				}
			)
		)
	})
	@PostMapping
	public ResponseEntity<SuccessResponse<SellerConcertDTO>> createConcert(
		@Parameter(
			description = "**판매자 ID** (1 이상의 양수)",
			example = "100",
			schema = @Schema(minimum = "1")
		)
		@RequestParam @Min(1) Long sellerId,

		@Parameter(
			description = "**콘서트 생성 정보**",
			required = true
		)
		@Valid @RequestBody SellerConcertCreateDTO createDTO) {

		SellerConcertDTO createdConcert = sellerConcertService
			.createConcert(sellerId, createDTO);

		return ResponseEntity.status(HttpStatus.CREATED)
			.body(SuccessResponse.of("콘서트가 생성되었습니다.", createdConcert));
	}

	@Operation(
		summary = "콘서트 수정",
		description = """
		기존 콘서트 정보를 수정합니다.
		부분 수정을 지원하며, 최소 하나의 필드는 수정되어야 합니다.
		"""
	)
	@ApiResponses({
		@ApiResponse(
			responseCode = "200",
			description = "콘서트 수정 성공",
			content = @Content(
				mediaType = "application/json",
				examples = @ExampleObject(
					name = "수정 성공 응답",
					value = """
					{
						"success": true,
						"message": "콘서트가 수정되었습니다.",
						"data": {
							"concertId": 1,
							"title": "아이유 콘서트 2025 'HEREH WORLD TOUR' - 서울",
							"artist": "아이유",
							"sellerId": 100,
							"status": "ON_SALE",
							"updatedAt": "2025-06-22T15:30:00"
						}
					}
					"""
				)
			)
		),
		@ApiResponse(responseCode = "400", description = "잘못된 입력값 또는 수정할 항목 없음"),
		@ApiResponse(responseCode = "403", description = "판매자 권한 없음"),
		@ApiResponse(responseCode = "404", description = "콘서트를 찾을 수 없음")
	})
	@PutMapping("/{concertId}")
	public ResponseEntity<SuccessResponse<SellerConcertDTO>> updateConcert(
		@Parameter(
			description = "**판매자 ID** (1 이상의 양수)",
			example = "100",
			schema = @Schema(minimum = "1")
		)
		@RequestParam @Min(1) Long sellerId,

		@Parameter(
			description = "**콘서트 ID** (1 이상의 양수)",
			example = "1",
			schema = @Schema(minimum = "1")
		)
		@PathVariable @Min(1) Long concertId,

		@Parameter(
			description = "**콘서트 수정 정보** (부분 수정 지원)",
			required = true
		)
		@Valid @RequestBody SellerConcertUpdateDTO updateDTO) {

		SellerConcertDTO updatedConcert = sellerConcertService
			.updateConcert(sellerId, concertId, updateDTO);

		return ResponseEntity.ok(SuccessResponse.of("콘서트가 수정되었습니다.", updatedConcert));
	}

	@Operation(
		summary = "포스터 이미지 업데이트",
		description = """
		콘서트의 포스터 이미지 URL만 별도로 수정합니다.
		이미지 업로드 후 URL 업데이트 시 사용됩니다.
		"""
	)
	@ApiResponses({
		@ApiResponse(
			responseCode = "200",
			description = "포스터 이미지 업데이트 성공",
			content = @Content(
				mediaType = "application/json",
				examples = @ExampleObject(
					name = "업데이트 성공 응답",
					value = """
					{
						"success": true,
						"message": "포스터 이미지가 업데이트되었습니다.",
						"data": null
					}
					"""
				)
			)
		),
		@ApiResponse(
			responseCode = "400",
			description = "잘못된 이미지 URL 형식",
			content = @Content(
				examples = @ExampleObject(
					value = """
					{
						"success": false,
						"message": "포스터 이미지 URL은 올바른 이미지 URL 형식이어야 합니다",
						"data": null
					}
					"""
				)
			)
		),
		@ApiResponse(responseCode = "403", description = "판매자 권한 없음"),
		@ApiResponse(responseCode = "404", description = "콘서트를 찾을 수 없음")
	})
	@PatchMapping("/{concertId}/poster")
	public ResponseEntity<SuccessResponse<Void>> updatePosterImage(
		@Parameter(
			description = "**판매자 ID** (1 이상의 양수)",
			example = "100",
			schema = @Schema(minimum = "1")
		)
		@RequestParam @Min(1) Long sellerId,

		@Parameter(
			description = "**콘서트 ID** (1 이상의 양수)",
			example = "1",
			schema = @Schema(minimum = "1")
		)
		@PathVariable @Min(1) Long concertId,

		@Parameter(
			description = "**포스터 이미지 URL 정보**",
			required = true
		)
		@Valid @RequestBody SellerConcertImageUpdateDTO imageDTO) {

		sellerConcertService.updatePosterImage(sellerId, concertId, imageDTO);
		return ResponseEntity.ok(SuccessResponse.of("포스터 이미지가 업데이트되었습니다.", null));
	}

	@Operation(
		summary = "콘서트 삭제 (취소 처리)",
		description = """
		콘서트를 삭제합니다. 실제로는 상태를 CANCELLED로 변경하여 논리적 삭제를 수행합니다.
		이미 예매된 티켓이 있는 경우에도 안전하게 처리됩니다.
		"""
	)
	@ApiResponses({
		@ApiResponse(
			responseCode = "200",
			description = "콘서트 취소 성공",
			content = @Content(
				mediaType = "application/json",
				examples = @ExampleObject(
					name = "취소 성공 응답",
					value = """
					{
						"success": true,
						"message": "콘서트가 취소되었습니다.",
						"data": null
					}
					"""
				)
			)
		),
		@ApiResponse(responseCode = "403", description = "판매자 권한 없음"),
		@ApiResponse(responseCode = "404", description = "콘서트를 찾을 수 없음")
	})
	@DeleteMapping("/{concertId}")
	public ResponseEntity<SuccessResponse<Void>> deleteConcert(
		@Parameter(
			description = "**판매자 ID** (1 이상의 양수)",
			example = "100",
			schema = @Schema(minimum = "1")
		)
		@RequestParam @Min(1) Long sellerId,

		@Parameter(
			description = "**콘서트 ID** (1 이상의 양수)",
			example = "1",
			schema = @Schema(minimum = "1")
		)
		@PathVariable @Min(1) Long concertId) {

		sellerConcertService.cancelConcert(sellerId, concertId);
		return ResponseEntity.ok(SuccessResponse.of("콘서트가 취소되었습니다.", null));
	}

	@Operation(
		summary = "판매자 콘서트 개수 조회",
		description = """
		특정 판매자가 등록한 전체 콘서트 개수를 조회합니다.
		모든 상태의 콘서트를 포함합니다.
		"""
	)
	@ApiResponses({
		@ApiResponse(
			responseCode = "200",
			description = "콘서트 개수 조회 성공",
			content = @Content(
				mediaType = "application/json",
				examples = @ExampleObject(
					name = "개수 조회 응답",
					value = """
					{
						"success": true,
						"message": "성공",
						"data": 27
					}
					"""
				)
			)
		),
		@ApiResponse(responseCode = "400", description = "잘못된 판매자 ID")
	})
	@GetMapping("/count")
	public ResponseEntity<SuccessResponse<Long>> getSellerConcertCount(
		@Parameter(
			description = "**판매자 ID** (1 이상의 양수)",
			example = "100",
			schema = @Schema(minimum = "1")
		)
		@RequestParam @Min(1) Long sellerId) {

		long count = sellerConcertService.getSellerConcertCount(sellerId);
		return ResponseEntity.ok(SuccessResponse.of(count));
	}

	@Operation(
		summary = "판매자 콘서트 AI 요약 수동 재생성",
		description = """
    판매자가 본인의 콘서트 AI 요약을 수동으로 재생성합니다.
    
    📋 **동작 조건**:
    - 본인 소유의 콘서트만 재생성 가능
    - 최소 리뷰 개수 조건 무시하고 강제 실행
    
    ⚠️ **주의사항**:
    - 판매자 권한 확인 후 실행
    - 리뷰가 없어도 재생성 시도
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
                    "data": "아이유의 2025년 새 앨범 발매 기념 월드투어 서울 공연으로, 신곡과 대표곡을 함께 들을 수 있는 특별한 무대입니다."
                }
                """
				)
			)
		),
		@ApiResponse(responseCode = "403", description = "판매자 권한 없음"),
		@ApiResponse(responseCode = "404", description = "콘서트를 찾을 수 없음"),
		@ApiResponse(responseCode = "500", description = "AI 서비스 오류")
	})
	@PostMapping("/{concertId}/ai-summary/regenerate")
	public ResponseEntity<SuccessResponse<String>> regenerateAiSummary(
		@RequestParam @Min(1) Long sellerId,
		@PathVariable @Min(1) Long concertId) {

		log.info("[SELLER] 판매자 AI 요약 수동 재생성 시작 - sellerId: {}, concertId: {}", sellerId, concertId);

		// 콘서트 조회 및 권한 확인
		Concert concert = concertService.getConcertEntityById(concertId)
			.orElseThrow(() -> new BusinessException(ErrorCode.CONCERT_NOT_FOUND));

		if (!concert.getSellerId().equals(sellerId)) {
			throw new BusinessException(ErrorCode.ACCESS_DENIED,
				"본인의 콘서트만 AI 요약을 재생성할 수 있습니다.");
		}

		try {
			List<Review> validReviews = reviewRepository.findValidReviewsForAiSummary(concertId);

			// 🔧 개선: 단계별 검증

			// 1단계: 리뷰가 아예 없는 경우
			if (validReviews.isEmpty()) {
				recordAiSummaryFailure(concert, "NO_REVIEWS", "리뷰가 없음");
				throw new BusinessException(ErrorCode.AI_SUMMARY_CONDITION_NOT_MET,
					"AI 요약 생성을 위한 리뷰가 없습니다. 먼저 리뷰를 작성해주세요.");
			}

			// 2단계: 리뷰는 있지만 최소 개수 미만인 경우 (판매자는 경고와 함께 진행)
			if (validReviews.size() < conditionProperties.getMinReviewCount()) {
				log.warn("[SELLER] 최소 리뷰 조건 미만이지만 판매자 요청으로 진행 - " +
						"concertId: {}, 현재리뷰: {}개, 권장최소: {}개",
					concertId, validReviews.size(), conditionProperties.getMinReviewCount());
			}

			// 3단계: 리뷰 내용 품질 검증 (10자 이상)
			long qualityReviews = validReviews.stream()
				.filter(review -> review.getDescription() != null)
				.filter(review -> review.getDescription().trim().length() >= 10)
				.count();

			if (qualityReviews == 0) {
				recordAiSummaryFailure(concert, "INSUFFICIENT_CONTENT", "유효한 리뷰 내용 부족");
				throw new BusinessException(ErrorCode.AI_SUMMARY_CONDITION_NOT_MET,
					"AI 요약 생성을 위해서는 최소 10자 이상의 리뷰 내용이 필요합니다. " +
						"현재 유효한 리뷰: " + qualityReviews + "개");
			}

			// 4단계: AI 요약 생성 처리
			log.info("[SELLER] AI 요약 생성 진행 - concertId: {}, 유효리뷰: {}개",
				concertId, qualityReviews);

			batchSummaryService.processConcertAiSummary(concert);
			String regeneratedSummary = concertService.getAiSummary(concertId);

			// 5단계: 성공 메시지 구성
			String successMessage;
			if (validReviews.size() < conditionProperties.getMinReviewCount()) {
				successMessage = String.format(
					"AI 요약이 생성되었습니다. (리뷰 %d개 기반, 권장 최소 %d개)\n" +
						"더 많은 리뷰가 쌓이면 품질이 향상됩니다.",
					validReviews.size(), conditionProperties.getMinReviewCount());
			} else {
				successMessage = "AI 요약이 성공적으로 생성되었습니다.";
			}

			return ResponseEntity.ok(SuccessResponse.of(successMessage, regeneratedSummary));

		} catch (BusinessException e) {
			if (!e.getErrorCode().equals(ErrorCode.AI_SUMMARY_CONDITION_NOT_MET)) {
				recordAiSummaryFailure(concert, "BUSINESS_ERROR", e.getMessage());
			}
			throw e;
		} catch (Exception e) {
			recordAiSummaryFailure(concert, "SYSTEM_ERROR", e.getMessage());
			log.error("[SELLER] AI 요약 생성 중 예상치 못한 오류 - concertId: {}", concertId, e);
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

			log.info("[SELLER] AI 요약 실패 정보 저장 완료: concertId={}, 실패유형={}, 재시도횟수={}, 실패시간={}",
				concert.getConcertId(), failureType, newRetryCount, now);

		} catch (Exception saveException) {
			log.error("[SELLER] AI 요약 실패 정보 저장 중 오류 발생: concertId={}",
				concert.getConcertId(), saveException);
		}
	}
}