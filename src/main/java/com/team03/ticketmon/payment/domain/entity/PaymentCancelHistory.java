package com.team03.ticketmon.payment.domain.entity;

import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Entity
@Table(name = "payment_cancel_history")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class PaymentCancelHistory {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long cancelHistoryId;

    @OneToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "payment_id", nullable = false, unique = true)
    private Payment payment;

    @Column(nullable = false, unique = true, length = 64)
    private String transactionKey;

    @Column(nullable = false, precision = 12, scale = 2)
    private BigDecimal cancelAmount;

    @Column(nullable = false, length = 200)
    private String cancelReason;

    @Column(nullable = false)
    private LocalDateTime canceledAt;

    @Column(nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @PrePersist
    private void onCreate() {
        this.createdAt = LocalDateTime.now();
    }

    @Builder
    public PaymentCancelHistory(Payment payment, String transactionKey, BigDecimal cancelAmount, String cancelReason,
                                LocalDateTime canceledAt) {
        this.payment = payment;
        this.transactionKey = transactionKey;
        this.cancelAmount = cancelAmount;
        this.cancelReason = cancelReason;
        this.canceledAt = canceledAt;
    }

}

