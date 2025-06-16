package com.team03.ticketmon._global.config;

import org.redisson.Redisson;
import org.redisson.api.RedissonClient;
import org.redisson.config.Config;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Redisson 설정 클래스
 * - Redis 분산 락, Pub/Sub, 캐시 기능을 위한 RedissonClient 설정
 * - Aiven Redis 서버 연결 설정
 */
@Configuration
public class RedissonConfig {

    @Value("${spring.data.redis.host}")
    private String redisHost;

    @Value("${spring.data.redis.port}")
    private int redisPort;

    @Value("${spring.data.redis.username}")
    private String redisUsername;

    @Value("${spring.data.redis.password}")
    private String redisPassword;

    /**
     * RedissonClient Bean 설정
     * - 단일 서버 모드로 Redis 연결
     * - 개발 환경에서는 로컬 Redis, 운영 환경에서는 Aiven Redis 사용
     */
    @Bean
    public RedissonClient redissonClient() {
        Config config = new Config();

        // Redis 연결 URL 구성
        // rediss://${USERNAME}:${PASSWORD}@${HOST}:${PORT}
        String redisUrl = "rediss://%s:%s@%s:%d".formatted(redisUsername, redisPassword, redisHost, redisPort);

        // 단일 서버 모드 설정
        config.useSingleServer()
                .setAddress(redisUrl)
                .setUsername(redisUsername.isEmpty() ? null : redisUsername)
                .setPassword(redisPassword.isEmpty() ? null : redisPassword)
                .setConnectionMinimumIdleSize(1)    // 최소 유휴 연결 수
                .setConnectionPoolSize(10)          // 연결 풀 크기
                .setRetryAttempts(3)                // 재시도 횟수
                .setRetryInterval(1000)             // 재시도 간격 (ms)
                .setTimeout(3000);                  // 타임아웃 (ms)

        return Redisson.create(config);
    }
}