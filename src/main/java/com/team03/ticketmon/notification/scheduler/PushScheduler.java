// src/main/java/com/team03/ticketmon/notification/scheduler/PushScheduler.java
package com.team03.ticketmon.notification.scheduler;

import com.team03.ticketmon.concert.domain.Concert;
import com.team03.ticketmon.concert.repository.ConcertRepository;
import com.team03.ticketmon.notification.domain.entity.Subscription;
import com.team03.ticketmon.notification.domain.enums.SubscriptionStatus;
import com.team03.ticketmon.notification.domain.enums.SubscriptionType;
import com.team03.ticketmon.notification.repository.SubscriptionRepository;
import com.team03.ticketmon.notification.service.PushService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.List;

@Component
@RequiredArgsConstructor
@Slf4j  // ← 추가
public class PushScheduler {
    private final ConcertRepository concertRepo;
    private final SubscriptionRepository subRepo;
    private final PushService pushService;

    /**
     * 공연 예매 시작 5분 전 PUSH 알림 발송
     * 매 분 0초마다 체크
     */
    @Scheduled(cron = "0 * * * * ?")
    public void sendBookingOpenReminders() {
        LocalDateTime now = LocalDateTime.now();
        LocalDateTime from = now.plusMinutes(5).withSecond(0).withNano(0);
        LocalDateTime to = now.plusMinutes(6).withSecond(0).withNano(0);

        log.info("▶ Scheduler fired at {}", now);
        log.info("   Searching for concerts with bookingStartDate ∈ [{} , {})", from, to);

        List<Concert> concerts = concertRepo.findByBookingStartDateBetween(from, to);
        log.info("   → {} concert(s) found", concerts.size());
        if (concerts.isEmpty()) {
            return;
        }

        List<Subscription> subs = subRepo.findByTypeAndStatus(
                SubscriptionType.PUSH,
                SubscriptionStatus.SUBSCRIBED
        );
        log.info("   → {} subscriber(s) found for PUSH", subs.size());

        for (Concert concert : concerts) {
            for (Subscription sub : subs) {
                log.info("   Sending push for concertId={} to playerId={}",
                        concert.getConcertId(), sub.getPlayerId());
                pushService.sendPush(sub, concert);
            }
        }
    }
}
