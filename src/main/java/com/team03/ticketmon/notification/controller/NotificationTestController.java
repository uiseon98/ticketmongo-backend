// src/main/java/com/team03/ticketmon/notification/controller/NotificationTestController.java
package com.team03.ticketmon.notification.controller;

import com.team03.ticketmon.concert.domain.Concert;
import com.team03.ticketmon.concert.repository.ConcertRepository;
import com.team03.ticketmon.notification.domain.entity.Subscription;
import com.team03.ticketmon.notification.domain.enums.SubscriptionStatus;
import com.team03.ticketmon.notification.domain.enums.SubscriptionType;
import com.team03.ticketmon.notification.repository.SubscriptionRepository;
import com.team03.ticketmon.notification.scheduler.PushScheduler;
import com.team03.ticketmon.notification.service.PushService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/notifications/test")
@RequiredArgsConstructor
public class NotificationTestController {

    private final PushScheduler       pushScheduler;
    private final PushService         pushService;
    private final ConcertRepository   concertRepo;
    private final SubscriptionRepository subRepo;

    /**
     * ① 전체 스케줄러 즉시 실행
     */
    @PostMapping("/run-scheduler")
    public ResponseEntity<String> runScheduler() {
        pushScheduler.sendBookingOpenReminders();
        return ResponseEntity.ok("Scheduler executed");
    }

    /**
     * ② 특정 콘서트에 대해 구독자 전원에게 즉시 푸시 발송
     */
    @PostMapping("/run-concert/{concertId}")
    public ResponseEntity<String> runConcertPush(@PathVariable Long concertId) {
        Concert concert = concertRepo.findById(concertId)
                .orElse(null);
        if (concert == null) {
            return ResponseEntity.notFound().build();
        }

        // PUSH로 SUBSCRIBED 상태인 모든 구독자
        List<Subscription> subs = subRepo.findByTypeAndStatus(
                SubscriptionType.PUSH,
                SubscriptionStatus.SUBSCRIBED
        );
        if (subs.isEmpty()) {
            return ResponseEntity
                    .ok("No subscribers for PUSH");
        }

        subs.forEach(sub -> pushService.sendPush(sub, concert));
        return ResponseEntity
                .ok("Pushed to " + subs.size() + " subscribers for concert " + concertId);
    }
}
