package com.team03.ticketmon.notification.service;

import com.team03.ticketmon.notification.domain.entity.Subscription;
import com.team03.ticketmon.notification.domain.enums.SubscriptionStatus;
import com.team03.ticketmon.notification.domain.enums.SubscriptionType;
import com.team03.ticketmon.notification.repository.SubscriptionRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;

@Slf4j
@Service
@RequiredArgsConstructor
public class SubscriptionService {

    private final SubscriptionRepository repo;

    @Transactional
    public void subscribe(Long userId, String playerId) {
        log.info("↳ 서비스 처리 시작: userId={}, playerId={}", userId, playerId);
        Subscription sub = repo.findByPlayerId(playerId)
                .map(existing -> {
                    existing.setStatus(SubscriptionStatus.SUBSCRIBED);
                    existing.setExpiresAt(LocalDateTime.now().plusYears(1));
                    return existing;
                })
                .orElse(Subscription.builder()
                        .userId(userId)
                        .playerId(playerId)
                        .type(SubscriptionType.PUSH)
                        .build()
                );
        repo.save(sub);
    }

    @Transactional
    public void unsubscribe(String playerId) {
        repo.findByPlayerId(playerId).ifPresent(sub -> {
            sub.setStatus(SubscriptionStatus.UNSUBSCRIBED);
            repo.save(sub);
        });
    }
}
