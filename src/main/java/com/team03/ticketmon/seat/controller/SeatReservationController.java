// src/main/java/com/team03/ticketmon/seat/controller/SeatReservationController.java
package com.team03.ticketmon.seat.controller;

import com.team03.ticketmon._global.exception.SuccessResponse;
import com.team03.ticketmon.auth.jwt.CustomUserDetails;
import com.team03.ticketmon.seat.domain.SeatStatus;
import com.team03.ticketmon.seat.dto.SeatStatusResponse;
import com.team03.ticketmon.seat.exception.SeatReservationException;
import com.team03.ticketmon.seat.service.SeatInfoHelper;
import com.team03.ticketmon.seat.service.SeatStatusService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.Optional;

/**
 * 좌석 예약 관리 컨트롤러
 * - 좌석 임시 선점 (분산 락 적용)
 * - 좌석 선점 해제 (권한 검증)
 * - 실시간 동시성 제어
 *
 * ✅ 개선사항:
 * - SeatInfoHelper 활용으로 실제 DB 조회
 * - 컨트롤러 레벨 분산 락 제거 (서비스 레이어에서 처리)
 * - 클린한 코드 구조
 */
@Tag(name = "좌석 예약 관리", description = "좌석 선점/해제 API (분산 락 적용)")
@Slf4j
@RestController
@RequestMapping("/api/seats")
@RequiredArgsConstructor
public class SeatReservationController {

    private final SeatStatusService seatStatusService;
    private final SeatInfoHelper seatInfoHelper; // ✅ 추가: 실제 DB 조회용

    /**
     * 좌석 임시 선점
     * ✅ 개선: 실제 DB에서 좌석 정보 조회, 서비스 레이어에서 분산 락 처리
     */
    @Operation(summary = "좌석 임시 선점", description = "좌석을 5분간 임시 선점합니다 (분산 락 적용)")
    @PostMapping("/concerts/{concertId}/seats/{seatId}/reserve")
    public ResponseEntity<SuccessResponse<SeatStatusResponse>> reserveSeat(
            @Parameter(description = "콘서트 ID", example = "1")
            @PathVariable Long concertId,
            @Parameter(description = "좌석 ID", example = "1")
            @PathVariable Long seatId,
            @AuthenticationPrincipal CustomUserDetails user) {

        try {
            log.info("좌석 선점 요청: concertId={}, seatId={}, userId={}",
                    concertId, seatId, user.getUserId());

            // 1. 현재 좌석 상태 확인 (빠른 검증)
            Optional<SeatStatus> currentStatus = seatStatusService.getSeatStatus(concertId, seatId);

            if (currentStatus.isPresent() && !currentStatus.get().isAvailable()) {
                SeatStatus seat = currentStatus.get();

                if (seat.isReserved() && user.getUserId().equals(seat.getUserId())) {
                    log.info("동일 사용자 재선점 요청: concertId={}, seatId={}, userId={}",
                            concertId, seatId, user.getUserId());
                    SeatStatusResponse response = SeatStatusResponse.from(seat);
                    return ResponseEntity.ok(SuccessResponse.of("이미 선점한 좌석입니다", response));
                }

                if (seat.isReserved() && !seat.isExpired()) {
                    return ResponseEntity.badRequest()
                            .body(SuccessResponse.of("다른 사용자가 선점 중인 좌석입니다", null));
                }

                if (seat.getStatus() == SeatStatus.SeatStatusEnum.BOOKED) {
                    return ResponseEntity.badRequest()
                            .body(SuccessResponse.of("이미 예매 완료된 좌석입니다", null));
                }
            }

            // 2. ✅ 핵심 개선: 실제 DB에서 좌석 정보 조회
            String seatInfo;
            try {
                seatInfo = seatInfoHelper.getSeatInfoFromDB(concertId, seatId);
                log.debug("DB에서 좌석 정보 조회 성공: concertId={}, seatId={}, seatInfo={}",
                        concertId, seatId, seatInfo);
            } catch (Exception e) {
                log.warn("DB 좌석 정보 조회 실패, 더미 데이터 사용: concertId={}, seatId={}, error={}",
                        concertId, seatId, e.getMessage());
                // 폴백: 더미 데이터 생성 (하위 호환성)
                seatInfo = seatInfoHelper.generateDummySeatInfo(seatId.intValue());
            }

            // 3. ✅ 개선: 서비스 레이어에서 분산 락 처리 (이중 락 문제 해결)
            SeatStatus reservedSeat = seatStatusService.reserveSeat(
                    concertId, seatId, user.getUserId(), seatInfo);

            SeatStatusResponse response = SeatStatusResponse.from(reservedSeat);

            log.info("좌석 선점 성공: concertId={}, seatId={}, userId={}, seatInfo={}",
                    concertId, seatId, user.getUserId(), seatInfo);

            return ResponseEntity.ok(SuccessResponse.of("좌석 선점 성공", response));

        } catch (SeatReservationException e) {
            log.warn("좌석 선점 실패: concertId={}, seatId={}, userId={}, message={}",
                    concertId, seatId, user.getUserId(), e.getMessage());
            return ResponseEntity.badRequest()
                    .body(SuccessResponse.of(e.getMessage(), null));
        } catch (Exception e) {
            log.error("좌석 선점 처리 중 예기치 않은 오류: concertId={}, seatId={}, userId={}",
                    concertId, seatId, user.getUserId(), e);
            return ResponseEntity.status(500)
                    .body(SuccessResponse.of("좌석 선점 처리 중 오류가 발생했습니다.", null));
        }
    }

    /**
     * 좌석 선점 해제
     * ✅ 개선: 깔끔한 예외 처리, 권한 검증 강화
     */
    @Operation(summary = "좌석 선점 해제", description = "임시 선점된 좌석을 해제합니다 (권한 검증 포함)")
    @DeleteMapping("/concerts/{concertId}/seats/{seatId}/release")
    public ResponseEntity<SuccessResponse<String>> releaseSeat(
            @Parameter(description = "콘서트 ID", example = "1")
            @PathVariable Long concertId,
            @Parameter(description = "좌석 ID", example = "1")
            @PathVariable Long seatId,
            @AuthenticationPrincipal CustomUserDetails user) {

        try {
            // 사용자 정보 검증
            if (user == null || user.getUserId() == null) {
                log.warn("인증되지 않은 사용자의 좌석 해제 시도: concertId={}, seatId={}", concertId, seatId);
                return ResponseEntity.status(401)
                        .body(SuccessResponse.of("사용자 인증이 필요합니다.", null));
            }

            log.info("좌석 해제 요청: concertId={}, seatId={}, userId={}",
                    concertId, seatId, user.getUserId());

            // 권한 검증을 포함한 좌석 해제 (서비스 레이어에서 처리)
            seatStatusService.releaseSeat(concertId, seatId, user.getUserId());

            log.info("좌석 선점 해제 성공: concertId={}, seatId={}, userId={}",
                    concertId, seatId, user.getUserId());

            return ResponseEntity.ok(SuccessResponse.of("좌석 선점 해제 성공", "SUCCESS"));

        } catch (SeatReservationException e) {
            log.warn("좌석 해제 실패: concertId={}, seatId={}, userId={}, message={}",
                    concertId, seatId, user.getUserId(), e.getMessage());
            return ResponseEntity.badRequest()
                    .body(SuccessResponse.of(e.getMessage(), null));
        } catch (Exception e) {
            log.error("좌석 해제 처리 중 오류: concertId={}, seatId={}, userId={}",
                    concertId, seatId, user.getUserId(), e);
            return ResponseEntity.status(500)
                    .body(SuccessResponse.of("좌석 해제 처리 중 오류가 발생했습니다.", null));
        }
    }

    // ✅ 기존 더미 메서드 제거: generateSeatInfo() 삭제
    // 이제 SeatInfoHelper.getSeatInfoFromDB() 사용
}