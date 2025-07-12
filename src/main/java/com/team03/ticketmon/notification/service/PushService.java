package com.team03.ticketmon.notification.service;

import com.team03.ticketmon.concert.domain.Concert;
import com.team03.ticketmon.notification.domain.entity.NotificationLog;
import com.team03.ticketmon.notification.domain.entity.Subscription;
import com.team03.ticketmon.notification.domain.enums.Channel;
import com.team03.ticketmon.notification.domain.enums.NotificationType;
import com.team03.ticketmon.notification.domain.enums.Status;
import com.team03.ticketmon.notification.repository.NotificationLogRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Mono;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Map;

/**
 * OneSignal 푸시 발송 로직을 담당하는 서비스
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class PushService {
    private final WebClient oneSignalWebClient;
    private final NotificationLogRepository logRepo;

    @Value("${onesignal.app-id}")
    private String appId;

    /**
     * 공연 예매 오픈 5분 전 푸시 발송
     */
    public void sendPush(Subscription sub, Concert concert) {
        // 1) PENDING 로그 생성
        NotificationLog logEntry = NotificationLog.builder()
                .bookingId(null)  // 공연 알림이므로 bookingId는 null
                .subscriptionId(sub.getSubscriptionId())
                .channel(Channel.PUSH)
                .type(NotificationType.CONCERT_REMINDER)
                .status(Status.PENDING)
                .createdAt(LocalDateTime.now())
                .build();
        logRepo.save(logEntry);

        // 2) Payload 생성
        String title = concert.getTitle() + "\n예매 시작 5분 전입니다!";
        String body = "지금 바로 예매하세요 (" +
                concert.getBookingStartDate().format(DateTimeFormatter.ofPattern("예매일자 : MM월 dd일 HH:mm")) +
                ")";
        String url = "/concerts/" + concert.getConcertId();

        Map<String, Object> payload = Map.of(
                "app_id", appId,
                "include_player_ids", List.of(sub.getPlayerId()),
                "headings", Map.of("en", title),
                "contents", Map.of("en", body),
                "url", url
        );

        // 3) 디버깅 로그
        log.info("[PushService] Sending OneSignal payload for concertId={} playerId={} payload={}",
                concert.getConcertId(), sub.getPlayerId(), payload);

        // 4) OneSignal REST 비동기 전송
        oneSignalWebClient.post()
                .uri("/notifications")
                .bodyValue(payload)
                .retrieve()
                .bodyToMono(Map.class)
                .flatMap(resp -> {
                    // 성공 처리
                    logEntry.setStatus(Status.SENT);
                    logEntry.setOnesignalNotificationId((String) resp.get("id"));
                    logEntry.setSentAt(LocalDateTime.now());
                    return Mono.fromRunnable(() -> logRepo.save(logEntry));
                })
                .doOnError(err -> {
                    // 실패 처리
                    log.error("[PushService] OneSignal API error", err);
                    logEntry.setStatus(Status.FAILED);
                    logEntry.setErrorMsg(err.getMessage());
                    logEntry.setSentAt(LocalDateTime.now());
                    logRepo.save(logEntry);
                })
                .subscribe();
    }
}
