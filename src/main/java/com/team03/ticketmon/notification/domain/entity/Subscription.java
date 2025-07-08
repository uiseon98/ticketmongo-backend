// src/main/java/com/team03/ticketmon/notification/domain/Subscription.java
package com.team03.ticketmon.notification.domain.entity;

import com.team03.ticketmon.notification.domain.enums.SubscriptionStatus;
import com.team03.ticketmon.notification.domain.enums.SubscriptionType;
import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;

@Entity
@Table(name = "subscriptions")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Subscription {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "subscription_id")
    private Long subscriptionId;

    @Column(name = "user_id", nullable = false)
    private Long userId;
    @Column(name = "player_id", nullable = true)
    private String playerId;

    @Enumerated(EnumType.STRING)
    @Column(name = "type", nullable = false)
    private SubscriptionType type;         // PUSH / EMAIL

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false)
    private SubscriptionStatus status;     // SUBSCRIBED / UNSUBSCRIBED

    private LocalDateTime expiresAt;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;

    @PrePersist
    public void prePersist() {
        createdAt = LocalDateTime.now();
        updatedAt = createdAt;
        status = SubscriptionStatus.SUBSCRIBED;
        expiresAt = createdAt.plusYears(1);  // 기본 만료 1년
    }

    @PreUpdate
    public void preUpdate() {
        updatedAt = LocalDateTime.now();
    }
}
