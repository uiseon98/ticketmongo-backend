package com.team03.ticketmon.queue.scheduler;

import com.team03.ticketmon.concert.domain.enums.ConcertStatus;
import com.team03.ticketmon.concert.repository.ConcertRepository;
import com.team03.ticketmon.queue.adapter.QueueRedisAdapter;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.redisson.api.RAtomicLong;
import org.redisson.api.RLock;
import org.redisson.api.RScoredSortedSet;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.util.Collection;
import java.util.List;
import java.util.concurrent.TimeUnit;

@Slf4j
@Component
@RequiredArgsConstructor
public class CleanupScheduler {

    private final ConcertRepository concertRepository;
    private final QueueRedisAdapter queueRedisAdapter;

    @Scheduled(fixedDelay = 7100)
    public void cleanupExpiredSessions() {
        RLock lock = queueRedisAdapter.getCleanupSchedulerLock();

        try {
            // 1. 분산 락 획득 시도
            boolean isLocked = lock.tryLock(100, 5, TimeUnit.SECONDS);
            if (!isLocked) {
                log.debug("===== 다른 세션 정리 스케줄러 인스턴스에서 스케줄러가 실행 중이므로, 현재 스케줄러는 건너뜁니다.");
                return;
            }

            // 2. 현재 ON_SALE 상태인 모든 콘서트 ID 목록을 가져옴
            List<Long> activeConcertIds = concertRepository.findConcertIdsByStatus(ConcertStatus.ON_SALE);
            if (activeConcertIds.isEmpty()) {
                log.debug("===== 현재 처리할 ON_SALE 상태의 콘서트가 없습니다.");
                return;
            }

            log.info("===== 세션 정리 스케줄러 실행 시작 (대상 콘서트 : {}개) =====", activeConcertIds.size());

            // 3. 각 콘서트별로 세션 정리 작업을 수행
            for (Long concertId : activeConcertIds) {
                RScoredSortedSet<Long> activeSessions = queueRedisAdapter.getActiveSessions(concertId);

                // 락이 걸려 있으므로, '조회'와 '삭제'를 순차적으로 실행해도 안전
                // 3-1. 만료된 멤버들을 조회
                Collection<Long> expiredUserIds = activeSessions.valueRange(
                        0, true,                  // 0점부터 (포함)
                        System.currentTimeMillis(), true // 현재 시간까지 (포함)
                );

                if (expiredUserIds != null && !expiredUserIds.isEmpty()) {
                    // 3-2. 조회된 멤버들을 삭제
                    activeSessions.removeAll(expiredUserIds);

                    // 3-3. 활성 사용자 수 감소
                    long expiredCount = expiredUserIds.size();
                    RAtomicLong activeUserCounter = queueRedisAdapter.getActiveUserCounter(concertId);

                    // compareAndSet을 이용한 원자적 업데이트 루프
                    long prevValue, nextValue;
                    do {
                        prevValue = activeUserCounter.get();
                        nextValue = Math.max(0, prevValue - expiredCount);
                    } while (!activeUserCounter.compareAndSet(prevValue, nextValue));

                    log.info("[콘서트 ID: {}] {}개의 세션 정리 완료. 남은 활성 사용자 수: {}", concertId, expiredCount, nextValue);
                }
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            log.error("세션 정리 스케줄러 락 획득 중 인터럽트 발생", e);
        } finally {
            if (lock.isHeldByCurrentThread()) {
                lock.unlock();
            }
            log.info("===== 세션 정리 스케줄러 실행 종료 =====");
        }
    }
}