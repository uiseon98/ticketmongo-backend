package com.team03.ticketmon.queue.domain;

public enum QueueStatus {
    WAITING,                // 대기 중
    IMMEDIATE_ENTRY,        // 즉시 입장 가능
    ADMITTED,               // 입장 허가된 상태
    ERROR,                  // 에러 발생
    EXPIRED_OR_NOT_IN_QUEUE // 대기열에 없거나 만료됨
}