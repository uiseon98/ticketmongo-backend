package com.team03.ticketmon.concert.controller;

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

/**
 * Seller Concert Controller
 * 판매자용 콘서트 관련 HTTP 요청 처리
 */
@Tag(name = "판매자용 콘서트 API", description = "판매자용 콘서트 등록, 수정, 관리 관련 API")
@RestController
@RequestMapping("/api/seller/concerts")
@RequiredArgsConstructor
@Validated
public class SellerConcertController {

	private final SellerConcertService sellerConcertService;

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
}