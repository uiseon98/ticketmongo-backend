package com.team03.ticketmon.concert.service;

import lombok.RequiredArgsConstructor;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;
import java.time.Duration;

/*
 * Cache Service
 * 캐싱 관련 비즈니스 로직 처리
 */

@Service
@RequiredArgsConstructor
public class CacheService {

	private final RedisTemplate<String, Object> redisTemplate;

	/**
	 * 콘서트 상세 정보 캐싱
	 */
	public void cacheConcertDetail(Long concertId, Object concertData) {
		String key = "concert:detail:" + concertId;
		redisTemplate.opsForValue().set(key, concertData, Duration.ofMinutes(30));
	}

	/**
	 * 캐싱된 콘서트 상세 정보 조회
	 */
	public Object getCachedConcertDetail(Long concertId) {
		String key = "concert:detail:" + concertId;
		return redisTemplate.opsForValue().get(key);
	}

	/**
	 * 검색 결과 캐싱
	 */
	public void cacheSearchResults(String keyword, Object searchResults) {
		String key = "search:" + keyword.toLowerCase();
		redisTemplate.opsForValue().set(key, searchResults, Duration.ofMinutes(15));
	}

	/**
	 * 캐싱된 검색 결과 조회
	 */
	public Object getCachedSearchResults(String keyword) {
		String key = "search:" + keyword.toLowerCase();
		return redisTemplate.opsForValue().get(key);
	}

	/**
	 * 사용자 예매 요약 캐싱
	 */
	public void cacheUserBookingSummary(Long userId, Object summary) {
		String key = "user:booking:summary:" + userId;
		redisTemplate.opsForValue().set(key, summary, Duration.ofMinutes(10));
	}

	/**
	 * 캐싱된 사용자 예매 요약 조회
	 */
	public Object getCachedUserBookingSummary(Long userId) {
		String key = "user:booking:summary:" + userId;
		return redisTemplate.opsForValue().get(key);
	}

	/**
	 * 캐시 무효화
	 */
	public void invalidateCache(String pattern) {
		redisTemplate.delete(pattern);
	}
}
