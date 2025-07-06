package com.team03.ticketmon.queue.strategy;

import org.redisson.api.RScoredSortedSet;

/**
 * 대기열 사용자에게 알림을 보내는 전략에 대한 공통 인터페이스
 * 이 인터페이스를 구현하여 다양한 알림 정책(예: 개인 순위 알림, 전체 현황 브로드캐스팅) 추가 가능
 */
public interface NotificationStrategy {

    /**
     * 특정 전략에 따라 알림을 전송합니다.
     *
     * @param concertId 알림 대상 콘서트 ID
     * @param queue 현재 대기열 상태를 담고 있는 RScoredSortedSet. 이 객체를 통해 필요한 정보를 조회합니다.
     */
    void execute(Long concertId, RScoredSortedSet<Long> queue);
}