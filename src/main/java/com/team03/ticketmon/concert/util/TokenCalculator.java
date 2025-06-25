package com.team03.ticketmon.concert.util;

import java.util.List;
import com.team03.ticketmon.concert.domain.Review;

/**
 * 토큰 계산 유틸리티 클래스
 * 순수 계산 로직만 수행하므로 static 메서드로 구현
 */
public final class TokenCalculator {

	// 한국어 기준 문자당 토큰 추정치
	private static final double CHARS_PER_TOKEN = 2.5;

	// 인스턴스 생성 방지
	private TokenCalculator() {}

	/**
	 * 리뷰 목록의 총 토큰 수 계산
	 */
	public static int calculateTotalTokens(List<Review> reviews) {
		int totalChars = 0;

		for (Review review : reviews) {
			totalChars += estimateReviewChars(review);
		}

		return (int) Math.ceil(totalChars / CHARS_PER_TOKEN);
	}

	/**
	 * 프롬프트 텍스트의 토큰 수 추정
	 */
	public static int estimateTokens(String text) {
		if (text == null || text.isEmpty()) return 0;
		return (int) Math.ceil(text.length() / CHARS_PER_TOKEN);
	}

	/**
	 * 개별 리뷰의 문자 수 계산
	 */
	private static int estimateReviewChars(Review review) {
		int chars = 0;

		if (review.getTitle() != null) {
			chars += review.getTitle().length();
		}
		if (review.getDescription() != null) {
			chars += review.getDescription().length();
		}
		if (review.getUserNickname() != null) {
			chars += review.getUserNickname().length();
		}

		// 평점, 구분자 등 추가 문자 고려
		chars += 50; // "평점: X점, 제목: , 내용: " 등

		return chars;
	}
}