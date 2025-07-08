package com.team03.ticketmon.notification.repository;

import com.team03.ticketmon.notification.domain.entity.Subscription;
import com.team03.ticketmon.notification.domain.enums.SubscriptionStatus;
import com.team03.ticketmon.notification.domain.enums.SubscriptionType;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface SubscriptionRepository extends JpaRepository<Subscription, Long> {
    Optional<Subscription> findByPlayerId(String playerId);

    List<Subscription> findByUserIdAndTypeAndStatus(Long userId, SubscriptionType type, SubscriptionStatus status);

}
