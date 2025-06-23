// src/main/java/com/team03/ticketmon/_global/config/RedisConfig.java
package com.team03.ticketmon._global.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.listener.RedisMessageListenerContainer;
import org.springframework.data.redis.serializer.GenericJackson2JsonRedisSerializer;
import org.springframework.data.redis.serializer.StringRedisSerializer;

/**
 * ✅ Redis 설정 클래스
 * <p>
 * Redis 연결, 직렬화 방식, 그리고 Key Expiration Event Listener를 설정합니다.
 * </p>
 *
 * 📌 주요 설정:
 * <ul>
 *   <li>Key: String 직렬화 (가독성)</li>
 *   <li>Value: JSON 직렬화 (복합 객체 저장 가능)</li>
 *   <li>Redis Key Expiration Events 활성화</li>
 * </ul>
 */
@Configuration
public class RedisConfig {

	/**
	 * RedisTemplate 빈 설정
	 *
	 * @param connectionFactory Redis 연결 팩토리 (자동 주입)
	 * @return 설정된 RedisTemplate
	 */
	@Bean
	public RedisTemplate<String, Object> redisTemplate(RedisConnectionFactory connectionFactory) {
		RedisTemplate<String, Object> template = new RedisTemplate<>();
		template.setConnectionFactory(connectionFactory);

		// Key는 String으로 직렬화 (가독성)
		template.setKeySerializer(new StringRedisSerializer());
		template.setHashKeySerializer(new StringRedisSerializer());

		// Value는 JSON으로 직렬화 (복합 객체 저장 가능)
		template.setValueSerializer(new GenericJackson2JsonRedisSerializer());
		template.setHashValueSerializer(new GenericJackson2JsonRedisSerializer());

		return template;
	}

	/**
	 * ✅ Redis Message Listener Container 설정
	 * - Redis Key Expiration Events를 수신하기 위한 컨테이너
	 * - valkey_notify_keyspace_events가 'Ex'로 설정되어야 동작함
	 *
	 * @param connectionFactory Redis 연결 팩토리
	 * @return Redis 메시지 리스너 컨테이너
	 */
	@Bean
	public RedisMessageListenerContainer redisMessageListenerContainer(RedisConnectionFactory connectionFactory) {
		RedisMessageListenerContainer container = new RedisMessageListenerContainer();
		container.setConnectionFactory(connectionFactory);

		// ✅ 이벤트 리스너 활성화를 위한 기본 설정
		// 실제 리스너는 SeatExpirationEventListener에서 등록됨

		return container;
	}
}