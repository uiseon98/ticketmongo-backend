package com.team03.ticketmon._global.config;

import com.fasterxml.jackson.annotation.JsonTypeInfo;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.databind.jsontype.BasicPolymorphicTypeValidator;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import org.springframework.cache.CacheManager;
import org.springframework.cache.annotation.EnableCaching;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;
import org.springframework.data.redis.cache.RedisCacheConfiguration;
import org.springframework.data.redis.cache.RedisCacheManager;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.data.redis.serializer.GenericJackson2JsonRedisSerializer;
import org.springframework.data.redis.serializer.RedisSerializationContext;
import org.springframework.data.redis.serializer.StringRedisSerializer;

import java.time.Duration;
import java.util.HashMap;
import java.util.Map;

/**
 * ✅ 캐시별 선택적 직렬화 설정
 * 기존 좌석 캐시: 타입 정보 없음 (호환성 유지)
 * 새로운 콘서트 캐시: 타입 정보 포함 (ClassCastException 해결)
 */
@Configuration
@EnableCaching
public class CacheConfig {

    /**
     * 기존 방식 ObjectMapper (타입 정보 없음)
     */
    @Bean
    @Primary
    public ObjectMapper legacyObjectMapper() {
        ObjectMapper objectMapper = new ObjectMapper();
        objectMapper.registerModule(new JavaTimeModule());
        objectMapper.disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);
        // 타입 정보 없음 - 기존 좌석 캐시와 호환
        return objectMapper;
    }

    /**
     * 새로운 방식 ObjectMapper (타입 정보 포함)
     */
    @Bean
    public ObjectMapper typedObjectMapper() {
        ObjectMapper objectMapper = new ObjectMapper();
        objectMapper.registerModule(new JavaTimeModule());
        objectMapper.disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);

        // ✅ 타입 정보 포함 - 콘서트 캐시용
        BasicPolymorphicTypeValidator typeValidator = BasicPolymorphicTypeValidator.builder()
            .allowIfSubType(Object.class)
            .build();

        objectMapper.activateDefaultTyping(
            typeValidator,
            ObjectMapper.DefaultTyping.NON_FINAL,
            JsonTypeInfo.As.PROPERTY
        );

        return objectMapper;
    }

    /**
     * Redis 기반 캐시 매니저 설정
     */
    @Bean
    public CacheManager cacheManager(RedisConnectionFactory connectionFactory) {

        // ✅ 기존 방식 직렬화 (좌석 캐시용)
        GenericJackson2JsonRedisSerializer legacySerializer =
            new GenericJackson2JsonRedisSerializer(legacyObjectMapper());

        // ✅ 타입 정보 포함 직렬화 (콘서트 캐시용)
        GenericJackson2JsonRedisSerializer typedSerializer =
            new GenericJackson2JsonRedisSerializer(typedObjectMapper());

        // 기본 캐시 설정 (기존 방식)
        RedisCacheConfiguration defaultCacheConfig = RedisCacheConfiguration.defaultCacheConfig()
            .entryTtl(Duration.ofMinutes(30))
            .serializeKeysWith(RedisSerializationContext.SerializationPair
                .fromSerializer(new StringRedisSerializer()))
            .serializeValuesWith(RedisSerializationContext.SerializationPair
                .fromSerializer(legacySerializer))
            .disableCachingNullValues();

        // 캐시별 개별 설정
        Map<String, RedisCacheConfiguration> cacheConfigurations = new HashMap<>();

        // ===== 기존 좌석 관련 캐시 (타입 정보 없음, 기존 호환성 유지) =====

        cacheConfigurations.put("seatInfo", defaultCacheConfig
            .entryTtl(Duration.ofHours(1)));

        cacheConfigurations.put("seatExists", defaultCacheConfig
            .entryTtl(Duration.ofHours(2)));

        cacheConfigurations.put("venueInfo", defaultCacheConfig
            .entryTtl(Duration.ofHours(12)));

        cacheConfigurations.put("concertQueueStatus", defaultCacheConfig
            .entryTtl(Duration.ofMinutes(5)));

        // ===== 새로운 콘서트 관련 캐시 (타입 정보 포함) =====

        RedisCacheConfiguration typedCacheConfig = RedisCacheConfiguration.defaultCacheConfig()
            .serializeKeysWith(RedisSerializationContext.SerializationPair
                .fromSerializer(new StringRedisSerializer()))
            .serializeValuesWith(RedisSerializationContext.SerializationPair
                .fromSerializer(typedSerializer))  // ✅ 타입 정보 포함 직렬화
            .disableCachingNullValues();

        cacheConfigurations.put("concertDetail", typedCacheConfig
            .entryTtl(Duration.ofMinutes(15)));

        cacheConfigurations.put("searchResults", typedCacheConfig
            .entryTtl(Duration.ofMinutes(10)));

        return RedisCacheManager.builder(connectionFactory)
            .cacheDefaults(defaultCacheConfig)
            .withInitialCacheConfigurations(cacheConfigurations)
            .build();
    }
}