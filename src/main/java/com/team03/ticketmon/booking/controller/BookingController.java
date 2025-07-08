package com.team03.ticketmon.booking.controller;

import com.team03.ticketmon._global.exception.SuccessResponse;
import com.team03.ticketmon.auth.jwt.CustomUserDetails;
import com.team03.ticketmon.booking.domain.Booking;
import com.team03.ticketmon.booking.dto.BookingCreateRequest;
import com.team03.ticketmon.booking.facade.BookingFacadeService;
import com.team03.ticketmon.booking.service.BookingService;
import com.team03.ticketmon.payment.dto.PaymentExecutionResponse;
import com.team03.ticketmon.payment.dto.PaymentResponseDto;
import com.team03.ticketmon.seat.service.SeatLockService;
import com.team03.ticketmon.seat.dto.BulkSeatLockResultDTO;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

/**
 * 예매(Booking)와 관련된 HTTP 요청을 처리하는 API 컨트롤러
 *
 * <p>
 * 이 컨트롤러는 클라이언트의 요청을 받아 서비스 계층에 전달하고,
 * 그 결과를 표준화된 응답 형식인 {@link SuccessResponse}로 감싸 반환
 * 모든 엔드포인트는 인증된 사용자만 접근 가능하다고 가정하며,
 * {@link AuthenticationPrincipal}을 통해 인증된 사용자 정보를 획득
 * </p>
 */
@Tag(name = "예약 API", description = "예매 생성, 취소 관련 API")
@Slf4j
@RestController
@RequestMapping("/api/bookings")
@RequiredArgsConstructor
public class BookingController {

    private final BookingFacadeService bookingFacadeService; // Facade 주입
    private final BookingService bookingService;
    private final SeatLockService seatLockService;

    @Operation(summary = "예매 생성 및 결제 준비", description = "좌석 영구 선점 후 예매를 생성하고, 즉시 결제에 필요한 정보를 반환합니다.")
    @ApiResponses({
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "201", description = "예매 정보 생성 성공"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "400", description = "유효하지 않은 입력 (좌석 ID 누락 등)"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "404", description = "존재하지 않는 콘서트 또는 좌석"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "409", description = "이미 선택되었거나 선점 정보가 유효하지 않은 좌석")
    })
    @PostMapping
    public ResponseEntity<SuccessResponse<PaymentExecutionResponse>> createBookingAndPreparePayment(
            @Valid @RequestBody BookingCreateRequest createRequest,
            @AuthenticationPrincipal CustomUserDetails user
    ) {
        log.info("예매 생성 및 결제 준비 시도. for user: {}", user.getUsername());

        try {
            // 1. 좌석 영구 선점 처리
            BulkSeatLockResultDTO lockResult = seatLockService.lockAllUserSeatsPermanently(
                    createRequest.getConcertId(), user.getUserId());
            
            if (!lockResult.isAllSuccess()) {
                log.warn("좌석 영구 선점 실패: {}", lockResult.getErrorMessage());
                return ResponseEntity.badRequest().body(
                        SuccessResponse.of("좌석 영구 선점에 실패했습니다: " + lockResult.getErrorMessage(), null)
                );
            }

            // 2. 예매 생성 및 결제 정보 조회
            PaymentExecutionResponse responseDto = bookingFacadeService.createBookingAndInitiatePayment(createRequest,
                    user.getUserId());

            log.info("예매 생성 및 좌석 영구 선점 완료. user: {}, lockResult: {}", 
                    user.getUsername(), lockResult.getSummary());

            return new ResponseEntity<>(SuccessResponse.of("예매 생성 및 결제 정보 조회가 완료되었습니다.", responseDto), HttpStatus.CREATED);

        } catch (Exception e) {
            log.error("예매 생성 중 예외 발생: user={}, concertId={}", 
                    user.getUsername(), createRequest.getConcertId(), e);
            
            // 실패 시 좌석 복원 시도
            try {
                seatLockService.restoreAllUserSeatsWithCompensation(createRequest.getConcertId(), user.getUserId(), true);
                log.info("예매 실패 후 좌석 복원 완료");
            } catch (Exception restoreException) {
                log.error("좌석 복원 중 예외 발생", restoreException);
            }
            
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(
                    SuccessResponse.of("예매 처리 중 오류가 발생했습니다.", null)
            );
        }
    }

    /**
     * 예매를 취소. 이 요청은 결제 취소를 포함한 모든 과정을 동기적으로 처리
     *
     * @param bookingId 취소할 예매의 ID
     * @param user      현재 인증된 사용자 정보
     * @return 취소 성공 메시지
     */
    @Operation(summary = "예매 취소 (동기)", description = "특정 예매를 취소 처리하고 좌석을 자동 복원합니다. 성공 시 연동된 결제도 함께 취소됩니다.")
    @ApiResponses({
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "취소 성공"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "401", description = "인증되지 않은 사용자 (토큰 없음)"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "403", description = "취소 권한 없음 (타인의 예매)"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "404", description = "존재하지 않는 예매"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "409", description = "상태 충돌 (이미 취소 또는 완료된 예매)")
    })
    @PostMapping("/{bookingId}/cancel")
    public ResponseEntity<SuccessResponse<Void>> cancelBooking(
            @Parameter(description = "취소할 예매의 ID", required = true) @PathVariable Long bookingId,
            @Parameter(hidden = true) @AuthenticationPrincipal CustomUserDetails user) {

        log.info("예매 취소 시도 booking ID: {} for user: {}", bookingId, user);

        try {
            // 1. 예매 정보 조회하여 콘서트 ID 획득
            Booking booking = bookingService.findByBookingNumberForUser(bookingId.toString(), user.getUserId());
            Long concertId = booking.getConcert().getConcertId();

            // 2. 예매 및 결제 취소 처리
            bookingFacadeService.cancelBookingAndPayment(bookingId, user.getUserId());

            // 3. 영구 선점된 좌석들 복원 (TTL 없이 완전 해제)
            try {
                BulkSeatLockResultDTO restoreResult = seatLockService.restoreAllUserSeatsWithCompensation(
                        concertId, user.getUserId(), false);

                if (restoreResult.isPartialSuccess()) {
                    log.info("예매 취소 후 좌석 복원 완료: {}", restoreResult.getSummary());
                } else {
                    log.warn("예매 취소 후 좌석 복원 부분 실패: {}", restoreResult.getErrorMessage());
                }
            } catch (Exception restoreException) {
                log.error("예매 취소 후 좌석 복원 중 예외 발생: bookingId={}, userId={}", 
                        bookingId, user.getUserId(), restoreException);
            }

            return ResponseEntity.ok(SuccessResponse.of("예매가 성공적으로 취소되었습니다.", null));

        } catch (Exception e) {
            log.error("예매 취소 중 예외 발생: bookingId={}, userId={}", bookingId, user.getUserId(), e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(
                    SuccessResponse.of("예매 취소 처리 중 오류가 발생했습니다.", null)
            );
        }
    }

    @Operation(summary = "예매 정보 조회", description = "bookingNumber로 예매 상세 정보를 반환합니다.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "조회 성공"),
            @ApiResponse(responseCode = "404", description = "존재하지 않는 예매번호")
    })
    @GetMapping("/{bookingNumber}")
    public ResponseEntity<SuccessResponse<PaymentResponseDto>> getBooking(
            @PathVariable String bookingNumber,
            @AuthenticationPrincipal CustomUserDetails user   // 로그인 사용자 검증이 필요 없으면 빼셔도 됩니다.
    ) {
        Booking booking = bookingService.findByBookingNumberForUser(bookingNumber, user.getUserId());

        PaymentResponseDto dto = new PaymentResponseDto(booking);
        return ResponseEntity.ok(SuccessResponse.of("예매 정보 조회가 완료되었습니다.", dto));
    }

}
