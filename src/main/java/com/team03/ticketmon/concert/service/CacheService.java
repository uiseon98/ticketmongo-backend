package com.team03.ticketmon.concert.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;
import java.time.Duration;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

/**
 * Cache Service (제네릭 개선 버전)
 * 타입 안전한 캐싱 관련 비즈니스 로직 처리
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class CacheService {

	private final RedisTemplate<String, Object> redisTemplate;

	/**
	 * 제네릭 캐시 저장 메서드
	 * @param key 캐시 키
	 * @param data 저장할 데이터
	 * @param duration 만료 시간
	 * @param <T> 데이터 타입
	 */
	public <T> void setCache(String key, T data, Duration duration) {
		try {
			log.info("🔄 Redis 캐시 저장 시도 - Key: {}, TTL: {}분", key, duration.toMinutes());
			redisTemplate.opsForValue().set(key, data, duration);
			log.info("✅ Redis 캐시 저장 성공 - Key: {}", key);
		} catch (Exception e) {
			log.error("❌ Redis 캐시 저장 실패 - Key: {}, Error: {}", key, e.getMessage(), e);
		}
	}

	/**
	 * 제네릭 캐시 조회 메서드
	 * @param key 캐시 키
	 * @param type 반환받을 데이터 타입 클래스
	 * @param <T> 데이터 타입
	 * @return Optional로 감싼 캐시 데이터
	 */
	@SuppressWarnings("unchecked")
	public <T> Optional<T> getCache(String key, Class<T> type) {
		try {
			log.debug("🔍 Redis 캐시 조회 시도 - Key: {}", key);
			Object cachedData = redisTemplate.opsForValue().get(key);

			if (cachedData != null && type.isInstance(cachedData)) {
				log.info("🎯 Redis 캐시 HIT - Key: {}", key);
				return Optional.of((T) cachedData);
			}

			log.info("💨 Redis 캐시 MISS - Key: {}", key);
			return Optional.empty();
		} catch (Exception e) {
			log.error("❌ Redis 캐시 조회 실패 - Key: {}, Error: {}", key, e.getMessage(), e);
			return Optional.empty();
		}
	}

	/**
	 * 콘서트 상세 정보 캐싱
	 */
	public <T> void cacheConcertDetail(Long concertId, T concertData) {
		String key = "concert:detail:" + concertId;
		log.info("🎵 콘서트 상세 정보 캐싱 - Concert ID: {}", concertId);
		setCache(key, concertData, Duration.ofMinutes(120));
	}


	/**
	 * 캐싱된 콘서트 상세 정보 조회 (타입 안전)
	 */
	public <T> Optional<T> getCachedConcertDetail(Long concertId, Class<T> type) {
		String key = "concert:detail:" + concertId;
		log.info("🎵 콘서트 상세 정보 캐시 조회 - Concert ID: {}", concertId);
		return getCache(key, type);
	}

	/**
	 * 검색 결과 캐싱
	 */
	public <T> void cacheSearchResults(String keyword, T searchResults) {
		String key = "search:" + keyword.toLowerCase();
		log.info("🔍 검색 결과 캐싱 - Keyword: '{}'", keyword);
		setCache(key, searchResults, Duration.ofMinutes(60));
	}

	/**
	 * 캐싱된 검색 결과 조회 (타입 안전)
	 */
	public <T> Optional<List<T>> getCachedSearchResults(String keyword, Class<T> elementType) {
		String key = "search:" + keyword.toLowerCase();
		log.info("🔍 검색 결과 캐시 조회 - Keyword: '{}'", keyword);

		try {
			Object cachedData = redisTemplate.opsForValue().get(key);
			if (cachedData instanceof List<?>) {
				List<?> list = (List<?>) cachedData;
				List<T> typedList = list.stream()
					.filter(elementType::isInstance)
					.map(elementType::cast)
					.collect(Collectors.toList());

				if (!typedList.isEmpty()) {
					log.info("🎯 검색 결과 캐시 HIT - Keyword: '{}', 결과 수: {}", keyword, typedList.size());
					return Optional.of(typedList);
				}
			}
			log.info("💨 검색 결과 캐시 MISS - Keyword: '{}'", keyword);
			return Optional.empty();
		} catch (Exception e) {
			log.error("❌ 검색 결과 캐시 조회 실패 - Keyword: '{}', Error: {}", keyword, e.getMessage(), e);
			return Optional.empty();
		}
	}
}