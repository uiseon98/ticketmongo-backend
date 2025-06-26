package com.team03.ticketmon.seat.scheduler;

import com.team03.ticketmon.concert.domain.Concert;
import com.team03.ticketmon.concert.repository.ConcertRepository;
import com.team03.ticketmon.seat.service.SeatCacheInitService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.redisson.api.RLock;
import org.redisson.api.RedissonClient;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.List;
import java.util.concurrent.TimeUnit;

/**
 * 좌석 캐시 자동 Warm-up 스케줄러
 * 기능:
 * - 예매 시작 10분 전에 자동으로 좌석 캐시 초기화
 * - 분산 락을 사용하여 중복 실행 방지
 * - 실패한 경우 재시도 로직 포함
 *
 * 스케줄링 주기: 5분마다 실행
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class SeatCacheWarmupScheduler {

    private final ConcertRepository concertRepository;
    private final SeatCacheInitService seatCacheInitService;
    private final RedissonClient redissonClient;

    // Redis 키 정의
    private static final String WARMUP_LOCK_KEY = "lock:seat:cache:warmup";
    private static final String PROCESSED_CONCERT_KEY_PREFIX = "processed:warmup:concert:";

    // 설정값
    private static final int WARMUP_MINUTES_BEFORE = 10; // 예매 시작 10분 전에 캐시 초기화
    private static final int LOCK_WAIT_TIME_SECONDS = 30; // 락 획득 대기 시간
    private static final int LOCK_LEASE_TIME_SECONDS = 300; // 락 유지 시간 (5분)

    /**
     * 5분마다 실행되는 자동 캐시 Warm-up 스케줄러
     * fixedDelay = 300000ms (5분)
     */
    @Scheduled(fixedDelay = 300000) // 5분마다 실행
    public void autoWarmupSeatCache() {
        RLock lock = redissonClient.getLock(WARMUP_LOCK_KEY);

        try {
            // 분산 락 획득 시도 (30초 대기, 5분 유지)
            boolean isLocked = lock.tryLock(LOCK_WAIT_TIME_SECONDS, LOCK_LEASE_TIME_SECONDS, TimeUnit.SECONDS);

            if (!isLocked) {
                log.debug("다른 인스턴스에서 캐시 Warm-up이 실행 중입니다. 현재 스케줄러는 건너뜁니다.");
                return;
            }

            log.info("===== 좌석 캐시 자동 Warm-up 스케줄러 시작 =====");

            // 예매 시작이 임박한 콘서트들 조회
            LocalDateTime targetTime = LocalDateTime.now().plusMinutes(WARMUP_MINUTES_BEFORE);
            List<Concert> upcomingConcerts = findUpcomingBookingStarts(targetTime);

            log.info("Warm-up 대상 콘서트 개수: {}", upcomingConcerts.size());

            if (upcomingConcerts.isEmpty()) {
                log.info("Warm-up 대상 콘서트가 없습니다.");
                return;
            }

            // 각 콘서트별로 캐시 초기화 실행
            int successCount = 0;
            int failureCount = 0;

            for (Concert concert : upcomingConcerts) {
                try {
                    // 이미 처리된 콘서트인지 확인
                    if (isAlreadyProcessed(concert.getConcertId())) {
                        log.debug("이미 처리된 콘서트입니다. concertId={}", concert.getConcertId());
                        continue;
                    }

                    // 좌석 캐시 초기화 실행
                    seatCacheInitService.initializeSeatCacheFromDB(concert.getConcertId());

                    // 처리 완료 마킹
                    markAsProcessed(concert.getConcertId());

                    successCount++;
                    log.info("좌석 캐시 Warm-up 성공: concertId={}, title={}, bookingStartDate={}",
                            concert.getConcertId(), concert.getTitle(), concert.getBookingStartDate());

                } catch (Exception e) {
                    failureCount++;
                    log.error("좌석 캐시 Warm-up 실패: concertId={}, title={}, error={}",
                            concert.getConcertId(), concert.getTitle(), e.getMessage(), e);
                }
            }

            log.info("===== 좌석 캐시 자동 Warm-up 완료: 성공={}, 실패={} =====",
                    successCount, failureCount);

        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            log.error("캐시 Warm-up 스케줄러 실행 중 인터럽트 발생", e);
        } catch (Exception e) {
            log.error("캐시 Warm-up 스케줄러 실행 중 예외 발생", e);
        } finally {
            // 락 해제
            if (lock.isHeldByCurrentThread()) {
                lock.unlock();
                log.debug("캐시 Warm-up 스케줄러 락 해제 완료");
            }
        }
    }

    /**
     * 예매 시작이 임박한 콘서트들을 조회합니다.
     *
     * @param targetTime 기준 시간 (현재 시간 + 10분)
     * @return 예매 시작이 임박한 콘서트 목록
     */
    private List<Concert> findUpcomingBookingStarts(LocalDateTime targetTime) {
        // 현재 시간부터 targetTime 사이에 예매가 시작되는 SCHEDULED 상태의 콘서트들을 조회
        LocalDateTime now = LocalDateTime.now();

        return concertRepository.findUpcomingBookingStarts(now, targetTime);
    }

    /**
     * 이미 처리된 콘서트인지 확인
     *
     * @param concertId 콘서트 ID
     * @return 처리 여부
     */
    private boolean isAlreadyProcessed(Long concertId) {
        String key = PROCESSED_CONCERT_KEY_PREFIX + concertId;
        return redissonClient.getBucket(key).isExists();
    }

    /**
     * 콘서트를 처리 완료로 마킹 (24시간 TTL)
     *
     * @param concertId 콘서트 ID
     */
    private void markAsProcessed(Long concertId) {
        String key = PROCESSED_CONCERT_KEY_PREFIX + concertId;
        // 24시간 후 자동 삭제 (중복 처리 방지용)
        redissonClient.getBucket(key).set("processed", 24, TimeUnit.HOURS);
    }
}