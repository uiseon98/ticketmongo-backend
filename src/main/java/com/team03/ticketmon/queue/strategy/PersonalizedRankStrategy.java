package com.team03.ticketmon.queue.strategy;

import com.team03.ticketmon.queue.service.NotificationService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.redisson.api.RScoredSortedSet;
import org.redisson.client.protocol.ScoredEntry;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import java.util.Collection;

@Slf4j
@Component
@RequiredArgsConstructor
public class PersonalizedRankStrategy implements NotificationStrategy {

    private final NotificationService notificationService;

    @Value("${app.queue.top-ranker-count}")
    private int topRankerCount;

    @Override
    public void execute(Long concertId, RScoredSortedSet<Long> queue) {
        if (queue == null || queue.isEmpty() || topRankerCount <= 0) {
            return;
        }

        // 1. 대기열의 최상위 N명의 ID와 점수를 조회합니다.
        Collection<ScoredEntry<Long>> topRankers = queue.entryRange(0, topRankerCount - 1);
        if (topRankers == null || topRankers.isEmpty()) {
            return;
        }

        log.debug("[Notification] 콘서트 ID {}: 최상위 {}명에게 개인 순위 알림 전송 시작.", concertId, topRankers.size());

        // 2. 각 사용자에게 개인화된 순위 정보를 1:1 메시지로 전송합니다.
        int rank = 1;
        for (ScoredEntry<Long> entry : topRankers) {
            Long userId = entry.getValue();

            try {
                // NotificationService에 개별 순위 전송을 위한 새 메서드 호출
                notificationService.sendRankUpdate(userId, rank);
            } catch (Exception e) {
                log.error("[Notification] 사용자 {}에게 순위 알림 전송 실패: {}", userId, e.getMessage());
            }
            rank++;
        }
    }
}