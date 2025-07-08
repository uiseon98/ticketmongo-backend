package com.team03.ticketmon.notification.dto;

import lombok.Data;

@Data
public class SubscriptionRequest {
    private String playerId;
    private String channel;  // "PUSH"
}
