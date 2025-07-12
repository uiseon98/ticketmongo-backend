package com.team03.ticketmon.notification.domain.entity;

import com.team03.ticketmon.notification.domain.enums.Channel;
import com.team03.ticketmon.notification.domain.enums.NotificationType;
import com.team03.ticketmon.notification.domain.enums.Status;
import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;

@Entity
@Table(name = "notification_logs")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class NotificationLog {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "notification_log_id")
    private Long notificationLogId;

    @Column(name = "booking_id", nullable = true)
    private Long bookingId;

    @Column(name = "subscription_id")
    private Long subscriptionId;

    @Column(name = "onesignal_notification_id", length = 255)
    private String onesignalNotificationId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private NotificationType type;        // e.g. CONCERT_REMINDER, BOOKING_CONFIRMED

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private Channel channel;              // PUSH or EMAIL

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private Status status;                // PENDING, SENT, FAILED, DELIVERED, OPENED

    private LocalDateTime sentAt;

    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;

    @Column(length = 1024)
    private String errorMsg;

    @PrePersist
    public void prePersist() {
        createdAt = LocalDateTime.now();
        updatedAt = createdAt;
        if (status == null) {
            status = Status.PENDING;
        }
    }

    @PreUpdate
    public void preUpdate() {
        updatedAt = LocalDateTime.now();
    }
}
