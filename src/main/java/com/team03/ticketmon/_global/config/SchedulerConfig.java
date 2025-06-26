package com.team03.ticketmon._global.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableScheduling;

/**
 * 스케줄러 설정 클래스
 * 기능:
 * - Spring의 @Scheduled 애노테이션 기반 스케줄러 활성화
 * - SeatCacheWarmupScheduler 등의 스케줄러 컴포넌트들이 동작할 수 있도록 설정
 */
@Configuration
@EnableScheduling
public class SchedulerConfig {

    /**
     * @EnableScheduling 애노테이션으로 스케줄러 기능을 활성화합니다.
     *
     * 활성화되는 스케줄러들:
     * - SeatCacheWarmupScheduler: 좌석 캐시 자동 Warm-up (5분마다)
     * - WaitingQueueScheduler: 대기열 처리 (10초마다) - 기존 구현
     *
     * 주의사항:
     * - 개발/테스트 환경에서는 스케줄러를 비활성화할 수 있습니다.
     * - application.yml에서 spring.task.scheduling.enabled: false로 설정 가능
     */

}