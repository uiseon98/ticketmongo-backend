package com.team03.ticketmon.queue.dto;

/**
 * ✅ RankUpdateEvent: 사용자 순위 업데이트 이벤트 DTO<br>
 * -----------------------------------------------------<br>
 * Redis Pub/Sub 채널을 통해 특정 사용자에게 현재 대기 순위를 전달합니다.<br><br>
 */
public record RankUpdateEvent(Long userId, int rank) {
}