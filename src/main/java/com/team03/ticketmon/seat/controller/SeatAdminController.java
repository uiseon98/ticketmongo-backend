package com.team03.ticketmon.seat.controller;

import com.team03.ticketmon._global.exception.SuccessResponse;
import com.team03.ticketmon.concert.domain.Concert;
import com.team03.ticketmon.concert.repository.ConcertRepository;
import com.team03.ticketmon.seat.scheduler.SeatCacheWarmupScheduler;
import com.team03.ticketmon.seat.service.SeatCacheInitService;
import com.team03.ticketmon.seat.service.SeatStatusService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.redisson.api.RedissonClient;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDateTime;
import java.util.*;

/**
 * 좌석 관리자 컨트롤러 (관리자 전용)
 * - 좌석 캐시 초기화/삭제
 * - 만료된 선점 좌석 정리
 * - 캐시 상태 모니터링
 *
 * 경로: src/main/java/com/team03/ticketmon/seat/controller/SeatAdminController.java
 *
 * 🔒 보안: 모든 API는 ADMIN 권한 필요 (테스트 진행이므로 주석 처리)
 */
@Tag(name = "좌석 관리자", description = "관리자 전용 좌석 캐시 관리 API")
@Slf4j
@RestController
@RequestMapping("/api/admin/seats")
@RequiredArgsConstructor
public class SeatAdminController {

    private final SeatCacheInitService seatCacheInitService;
    private final SeatStatusService seatStatusService;
    private final SeatCacheWarmupScheduler seatCacheWarmupScheduler;
    private final RedissonClient redissonClient;
    private final ConcertRepository concertRepository;

    /**
     * ✨ DB 기반 좌석 캐시 초기화 - 새로 추가된 API
     * 실제 DB 데이터를 기반으로 좌석 상태 캐시를 초기화합니다.
     */
    @Operation(summary = "DB 기반 좌석 캐시 초기화",
            description = "실제 DB의 콘서트 좌석 데이터를 기반으로 캐시를 초기화합니다. 예매 완료된 좌석은 BOOKED 상태로, 나머지는 AVAILABLE 상태로 설정됩니다.")
    // @PreAuthorize("hasRole('ADMIN')") // ← 📌 실제 서비스에서는 주석 해제
    @PostMapping("/concerts/{concertId}/cache/init-from-db")
    public ResponseEntity<SuccessResponse<String>> initSeatCacheFromDB(
            @Parameter(description = "콘서트 ID", example = "1")
            @PathVariable Long concertId) {

        try {
            // DB 기반 캐시 초기화 실행
            seatCacheInitService.initializeSeatCacheFromDB(concertId);

            log.info("DB 기반 좌석 캐시 초기화 완료: concertId={}", concertId);
            return ResponseEntity.ok(SuccessResponse.of("DB 기반 좌석 캐시 초기화 성공", "SUCCESS"));

        } catch (Exception e) {
            log.error("DB 기반 좌석 캐시 초기화 중 오류: concertId={}", concertId, e);
            return ResponseEntity.status(500)
                    .body(SuccessResponse.of("DB 기반 캐시 초기화 중 오류가 발생했습니다: " + e.getMessage(), "ERROR"));
        }
    }

    /**
     * 좌석 캐시 상태 조회 - 기존 API
     */
    @Operation(summary = "좌석 캐시 상태 조회", description = "특정 콘서트의 좌석 캐시 상태를 조회합니다")
    @GetMapping("/concerts/{concertId}/cache/status")
    public ResponseEntity<SuccessResponse<Map<String, Object>>> getCacheStatus(
            @Parameter(description = "콘서트 ID", example = "1")
            @PathVariable Long concertId) {

        try {
            Map<String, Object> status = seatCacheInitService.getCacheStatus(concertId);
            return ResponseEntity.ok(SuccessResponse.of("캐시 상태 조회 성공", status));

        } catch (Exception e) {
            log.error("캐시 상태 조회 중 오류: concertId={}", concertId, e);
            return ResponseEntity.status(500)
                    .body(SuccessResponse.of("캐시 상태 조회 중 오류가 발생했습니다.", null));
        }
    }

    /**
     * 좌석 캐시 삭제 - 기존 API
     */
    @Operation(summary = "좌석 캐시 삭제", description = "특정 콘서트의 좌석 캐시를 삭제합니다")
    // @PreAuthorize("hasRole('ADMIN')") // ← 📌 실제 서비스에서는 주석 해제
    @DeleteMapping("/concerts/{concertId}/cache")
    public ResponseEntity<SuccessResponse<String>> clearSeatCache(
            @Parameter(description = "콘서트 ID", example = "1")
            @PathVariable Long concertId) {

        try {
            String result = seatCacheInitService.clearSeatCache(concertId);
            return ResponseEntity.ok(SuccessResponse.of(result, "SUCCESS"));

        } catch (Exception e) {
            log.error("좌석 캐시 삭제 중 오류: concertId={}", concertId, e);
            return ResponseEntity.status(500)
                    .body(SuccessResponse.of("좌석 캐시 삭제 중 오류가 발생했습니다.", "ERROR"));
        }
    }

    /**
     * 만료된 선점 좌석 정리 - 기존 API
     */
    @Operation(summary = "만료된 선점 좌석 정리", description = "특정 콘서트의 만료된 선점 좌석들을 일괄 정리합니다")
    // @PreAuthorize("hasRole('ADMIN')") // ← 📌 실제 서비스에서는 주석 해제
    @PostMapping("/concerts/{concertId}/cleanup-expired")
    public ResponseEntity<SuccessResponse<String>> cleanupExpiredReservations(
            @Parameter(description = "콘서트 ID", example = "1")
            @PathVariable Long concertId) {

        try {
            seatStatusService.cleanupExpiredReservations(concertId);

            log.info("만료된 선점 좌석 정리 완료: concertId={}", concertId);
            return ResponseEntity.ok(SuccessResponse.of("만료된 선점 좌석 정리 완료", "SUCCESS"));

        } catch (Exception e) {
            log.error("만료된 선점 좌석 정리 중 오류: concertId={}", concertId, e);
            return ResponseEntity.status(500)
                    .body(SuccessResponse.of("만료된 선점 좌석 정리 중 오류가 발생했습니다.", "ERROR"));
        }
    }

    /**
     * 수동으로 캐시 Warm-up 스케줄러 실행
     * 테스트 및 긴급 상황 대응용
     */
    @Operation(summary = "수동 캐시 Warm-up 스케줄러 실행",
            description = "예매 시작이 임박한 콘서트들의 좌석 캐시를 수동으로 초기화합니다.")
    // @PreAuthorize("hasRole('ADMIN')") // ← 📌 실제 서비스에서는 주석 해제
    @PostMapping("/cache/warmup/manual")
    public ResponseEntity<SuccessResponse<Map<String, Object>>> manualWarmupCache() {
        try {
            log.info("수동 캐시 Warm-up 스케줄러 실행 요청");

            // 스케줄러 메서드 직접 호출
            long startTime = System.currentTimeMillis();
            seatCacheWarmupScheduler.autoWarmupSeatCache();
            long executionTime = System.currentTimeMillis() - startTime;

            Map<String, Object> result = new HashMap<>();
            result.put("message", "수동 캐시 Warm-up 실행 완료");
            result.put("executionTimeMs", executionTime);
            result.put("timestamp", LocalDateTime.now());

            return ResponseEntity.ok(SuccessResponse.of("수동 캐시 Warm-up 성공", result));

        } catch (Exception e) {
            log.error("수동 캐시 Warm-up 실행 중 오류", e);
            return ResponseEntity.status(500)
                    .body(SuccessResponse.of("수동 캐시 Warm-up 실행 중 오류가 발생했습니다: " + e.getMessage(), null));
        }
    }

    /**
     * ✅ 수정된 예매 시작이 임박한 콘서트 목록 조회
     * 스케줄러 상태 확인용
     */
    @Operation(summary = "Warm-up 대상 콘서트 조회",
            description = "현재 캐시 Warm-up 대상인 콘서트들을 조회합니다.")
    @GetMapping("/cache/warmup/targets")
    public ResponseEntity<SuccessResponse<Map<String, Object>>> getWarmupTargets() {
        try {
            LocalDateTime now = LocalDateTime.now();
            LocalDateTime targetTime = now.plusMinutes(10);

            // 예매 시작이 임박한 콘서트들 조회
            List<Concert> upcomingConcerts = concertRepository.findUpcomingBookingStarts(now, targetTime);

            // ✅ 수정: HashMap을 사용하여 명시적으로 Map 생성
            List<Map<String, Object>> concertInfos = new ArrayList<>();
            for (Concert concert : upcomingConcerts) {
                Map<String, Object> concertInfo = new HashMap<>();
                concertInfo.put("concertId", concert.getConcertId());
                concertInfo.put("title", concert.getTitle());
                concertInfo.put("artist", concert.getArtist());
                concertInfo.put("bookingStartDate", concert.getBookingStartDate());
                concertInfo.put("totalSeats", concert.getTotalSeats());
                concertInfo.put("status", concert.getStatus().toString());
                concertInfo.put("minutesUntilBooking", java.time.Duration.between(now, concert.getBookingStartDate()).toMinutes());
                concertInfos.add(concertInfo);
            }

            // ✅ 수정: HashMap을 사용하여 명시적으로 Map 생성
            Map<String, Object> targetTimeRange = new HashMap<>();
            targetTimeRange.put("from", now);
            targetTimeRange.put("to", targetTime);

            Map<String, Object> result = new HashMap<>();
            result.put("targetConcerts", concertInfos);
            result.put("totalCount", concertInfos.size());
            result.put("checkTime", now);
            result.put("targetTimeRange", targetTimeRange);

            return ResponseEntity.ok(SuccessResponse.of("Warm-up 대상 콘서트 조회 성공", result));

        } catch (Exception e) {
            log.error("Warm-up 대상 콘서트 조회 중 오류", e);
            return ResponseEntity.status(500)
                    .body(SuccessResponse.of("Warm-up 대상 콘서트 조회 중 오류가 발생했습니다.", null));
        }
    }

    /**
     * ✅ 수정된 캐시 Warm-up 처리 이력 조회 (간단한 버전)
     * Redis에서 처리 완료된 콘서트들의 상태를 확인
     */
    @Operation(summary = "캐시 Warm-up 처리 이력 조회",
            description = "최근 24시간 내 캐시 Warm-up이 처리된 콘서트들의 이력을 조회합니다.")
    @GetMapping("/cache/warmup/history")
    public ResponseEntity<SuccessResponse<Map<String, Object>>> getWarmupHistory() {
        try {
            // ✅ 간단한 방법: 패턴 매칭 대신 직접 확인
            List<Map<String, Object>> processedConcerts = new ArrayList<>();

            // 최근 등록된 콘서트들 중에서 처리된 것들만 확인
            List<Concert> recentConcerts = concertRepository.findAll(); // 또는 최근 콘서트만 조회

            for (Concert concert : recentConcerts) {
                String processedKey = "processed:warmup:concert:" + concert.getConcertId();
                boolean isProcessed = redissonClient.getBucket(processedKey).isExists();

                if (isProcessed) {
                    Map<String, Object> concertInfo = new HashMap<>();
                    concertInfo.put("concertId", concert.getConcertId());
                    concertInfo.put("title", concert.getTitle());
                    concertInfo.put("artist", concert.getArtist());
                    concertInfo.put("bookingStartDate", concert.getBookingStartDate());
                    concertInfo.put("status", concert.getStatus().toString());
                    concertInfo.put("processedAt", "최근 24시간 내");
                    processedConcerts.add(concertInfo);
                }
            }

            Map<String, Object> result = new HashMap<>();
            result.put("processedConcerts", processedConcerts);
            result.put("totalProcessedCount", processedConcerts.size());
            result.put("checkTime", LocalDateTime.now());

            return ResponseEntity.ok(SuccessResponse.of("Warm-up 처리 이력 조회 성공", result));

        } catch (Exception e) {
            log.error("Warm-up 처리 이력 조회 중 오류", e);
            return ResponseEntity.status(500)
                    .body(SuccessResponse.of("Warm-up 처리 이력 조회 중 오류가 발생했습니다.", null));
        }
    }
}