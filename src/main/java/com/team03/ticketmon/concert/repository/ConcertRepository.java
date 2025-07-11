package com.team03.ticketmon.concert.repository;

import com.team03.ticketmon.concert.domain.Concert;
import com.team03.ticketmon.concert.domain.enums.ConcertStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import java.time.LocalDate;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

/*
 * Concert Repository
 * 콘서트 데이터 접근 계층
 *
 * 🔥 주요 변경사항: 모든 조회 쿼리에서 COMPLETED, CANCELLED 상태 제외
 */

@Repository
public interface ConcertRepository extends JpaRepository<Concert, Long> {

	/**
	 * 🔥 키워드로 콘서트 검색 - COMPLETED/CANCELLED 제외
	 */
	@Query("SELECT c FROM Concert c WHERE " +
		"c.status IN ('SCHEDULED', 'ON_SALE', 'SOLD_OUT') AND " +  // 🔥 COMPLETED, CANCELLED 제외
		"(LOWER(c.title) LIKE LOWER(CONCAT('%', :keyword, '%')) OR " +
		"LOWER(c.artist) LIKE LOWER(CONCAT('%', :keyword, '%')) OR " +
		"LOWER(c.venueName) LIKE LOWER(CONCAT('%', :keyword, '%'))) " +
		"ORDER BY c.concertDate ASC")
	List<Concert> findByKeyword(@Param("keyword") String keyword);

	/**
	 * 🔥 날짜 범위로 콘서트 조회 - COMPLETED/CANCELLED 제외
	 */
	@Query("SELECT c FROM Concert c WHERE " +
		"c.status IN ('SCHEDULED', 'ON_SALE', 'SOLD_OUT') AND " +  // 🔥 COMPLETED, CANCELLED 제외
		"(:startDate IS NULL OR c.concertDate >= :startDate) AND " +
		"(:endDate IS NULL OR c.concertDate <= :endDate) " +
		"ORDER BY c.concertDate ASC")
	List<Concert> findByDateRange(@Param("startDate") LocalDate startDate,
		@Param("endDate") LocalDate endDate);

	/**
	 * 🔥 가격 범위로 콘서트 조회 - COMPLETED/CANCELLED 제외
	 */
	@Query("SELECT DISTINCT c FROM Concert c " +
		"JOIN c.concertSeats cs " +
		"WHERE c.status IN ('SCHEDULED', 'ON_SALE', 'SOLD_OUT') AND " +  // 🔥 COMPLETED, CANCELLED 제외
		"(:minPrice IS NULL OR cs.price >= :minPrice) AND " +
		"(:maxPrice IS NULL OR cs.price <= :maxPrice) " +
		"ORDER BY c.concertDate ASC")
	List<Concert> findByPriceRange(@Param("minPrice") BigDecimal minPrice,
		@Param("maxPrice") BigDecimal maxPrice);

	/**
	 * 🔥 날짜와 가격 범위로 콘서트 조회 - COMPLETED/CANCELLED 제외
	 */
	@Query("SELECT DISTINCT c FROM Concert c " +
		"JOIN c.concertSeats cs " +
		"WHERE c.status IN ('SCHEDULED', 'ON_SALE', 'SOLD_OUT') AND " +  // 🔥 COMPLETED, CANCELLED 제외
		"(:startDate IS NULL OR c.concertDate >= :startDate) AND " +
		"(:endDate IS NULL OR c.concertDate <= :endDate) AND " +
		"(:minPrice IS NULL OR cs.price >= :minPrice) AND " +
		"(:maxPrice IS NULL OR cs.price <= :maxPrice) " +
		"ORDER BY c.concertDate ASC")
	List<Concert> findByDateAndPriceRange(@Param("startDate") LocalDate startDate,
		@Param("endDate") LocalDate endDate,
		@Param("minPrice") BigDecimal minPrice,
		@Param("maxPrice") BigDecimal maxPrice);

	@EntityGraph(attributePaths = {"concertSeats"})
	Page<Concert> findByStatusOrderByConcertDateAsc(ConcertStatus status,
		Pageable pageable);

	List<Concert> findByStatusOrderByConcertDateAsc(ConcertStatus status);

	List<Concert> findByConcertDateAndStatusOrderByConcertDateAsc(LocalDate concertDate,
		ConcertStatus status);

	List<Concert> findByStatusInOrderByConcertDateAsc(List<ConcertStatus> statuses);

	/**
	 * 🔥 페이징된 상태별 콘서트 조회 - COMPLETED/CANCELLED 자동 제외
	 */
	@Query("SELECT c FROM Concert c WHERE " +
		"c.status IN :statuses AND " +
		"c.status NOT IN ('COMPLETED', 'CANCELLED') " +  // 🔥 명시적으로 제외
		"ORDER BY c.concertDate ASC")
	Page<Concert> findByStatusInOrderByConcertDateAscExcludingCompleted(
		@Param("statuses") List<ConcertStatus> statuses,
		Pageable pageable);

	/**
	 * 🔥 기본 콘서트 목록 조회 (페이징 + 정렬) - COMPLETED/CANCELLED 제외
	 * 정렬은 Pageable의 Sort 정보를 사용하여 동적으로 처리
	 */
	@Query("SELECT c FROM Concert c WHERE " +
		"c.status IN ('SCHEDULED', 'ON_SALE', 'SOLD_OUT')")
	Page<Concert> findActiveConcerts(Pageable pageable);

	/**
	 * 🔥 기본 콘서트 목록 조회 (페이징 없음, 기본 정렬) - COMPLETED/CANCELLED 제외
	 */
	@Query("SELECT c FROM Concert c WHERE " +
		"c.status IN ('SCHEDULED', 'ON_SALE', 'SOLD_OUT') " +
		"ORDER BY c.concertDate ASC")
	List<Concert> findActiveConcerts();

	/**
	 * 예매 가능한 콘서트 조회
	 */
	@Query("SELECT c FROM Concert c WHERE " +
		"c.status = 'ON_SALE' AND " +
		"c.bookingStartDate <= CURRENT_TIMESTAMP AND " +
		"c.bookingEndDate >= CURRENT_TIMESTAMP " +
		"ORDER BY c.concertDate ASC")
	List<Concert> findBookableConcerts();

	/**
	 * 리뷰 변동성 기반 업데이트 대상 조회
	 */
	@Query("SELECT c FROM Concert c WHERE " +
		"(c.aiSummary IS NULL OR c.aiSummary = '') AND " +
		"(SELECT COUNT(r) FROM Review r WHERE r.concert = c AND r.description IS NOT NULL AND r.description != '') >= :minReviewCount")
	List<Concert> findConcertsNeedingInitialAiSummary(@Param("minReviewCount") Integer minReviewCount);

	@Query("SELECT c FROM Concert c WHERE " +
		"c.aiSummary IS NOT NULL AND c.aiSummary != '' AND " +
		"c.lastReviewModifiedAt > c.aiSummaryGeneratedAt AND " +
		"(SELECT COUNT(r) FROM Review r WHERE r.concert = c AND r.description IS NOT NULL AND r.description != '') >= :minReviewCount")
	List<Concert> findConcertsNeedingAiSummaryUpdate(@Param("minReviewCount") Integer minReviewCount);

	@Query("SELECT c FROM Concert c WHERE " +
		"c.aiSummary IS NOT NULL AND c.aiSummary != '' AND " +
		"c.aiSummaryGeneratedAt < :beforeTime AND " +
		"(SELECT COUNT(r) FROM Review r WHERE r.concert = c AND r.description IS NOT NULL AND r.description != '') >= :minReviewCount")
	List<Concert> findConcertsWithOutdatedSummary(@Param("beforeTime") LocalDateTime beforeTime,
		@Param("minReviewCount") Integer minReviewCount);

	/**
	 * 리뷰 수 변화가 큰 콘서트들
	 */
	@Query("SELECT c FROM Concert c WHERE " +
		"c.aiSummary IS NOT NULL AND " +
		"ABS((SELECT COUNT(r) FROM Review r WHERE r.concert = c AND r.description IS NOT NULL AND r.description != '') - c.aiSummaryReviewCount) >= :significantChange")
	List<Concert> findConcertsWithSignificantReviewCountChange(@Param("significantChange") Integer significantChange);

	/**
	 * 사전 필터링: 최소 리뷰 수 이상인 콘서트들 조회
	 */
	@Query("SELECT c FROM Concert c WHERE " +
		"(SELECT COUNT(r) FROM Review r WHERE r.concert = c " +
		"AND r.description IS NOT NULL AND TRIM(r.description) != '' " +
		"AND LENGTH(TRIM(r.description)) >= 10) >= :minReviewCount")
	List<Concert> findConcertsWithMinimumReviews(@Param("minReviewCount") Integer minReviewCount);

	/**
	 * ✅ [좌석 관리 및 예매 모듈] 파트에 필요한 메서드 추가
	 */

	/**
	 * 예매 시작이 임박한 콘서트들 조회 (캐시 Warm-up용)
	 * 지정된 시간 범위 내에 예매가 시작되는 SCHEDULED 상태의 콘서트들을 조회합니다.
	 *
	 * @param startTime 조회 시작 시간 (현재 시간)
	 * @param endTime 조회 종료 시간 (현재 시간 + 10분)
	 * @return 예매 시작이 임박한 콘서트 목록
	 */
	@Query("SELECT c FROM Concert c WHERE " +
		"c.status = 'SCHEDULED' AND " +
		"c.bookingStartDate BETWEEN :startTime AND :endTime " +
		"ORDER BY c.bookingStartDate ASC")
	List<Concert> findUpcomingBookingStarts(@Param("startTime") LocalDateTime startTime,
		@Param("endTime") LocalDateTime endTime);

	/**
	 * 예매 시작 시간 기준으로 특정 시간 이후에 시작되는 콘서트들 조회
	 *
	 * @param afterTime 기준 시간
	 * @return 기준 시간 이후에 예매가 시작되는 콘서트 목록
	 */
	@Query("SELECT c FROM Concert c WHERE " +
		"c.status = 'SCHEDULED' AND " +
		"c.bookingStartDate > :afterTime " +
		"ORDER BY c.bookingStartDate ASC")
	List<Concert> findConcertsBookingStartsAfter(@Param("afterTime") LocalDateTime afterTime);

	/**
	 * 오늘 예매가 시작되는 콘서트들 조회
	 *
	 * @param todayStart 오늘 00:00:00
	 * @param todayEnd 오늘 23:59:59
	 * @return 오늘 예매가 시작되는 콘서트 목록
	 */
	@Query("SELECT c FROM Concert c WHERE " +
		"c.status = 'SCHEDULED' AND " +
		"c.bookingStartDate BETWEEN :todayStart AND :todayEnd " +
		"ORDER BY c.bookingStartDate ASC")
	List<Concert> findTodayBookingStarts(@Param("todayStart") LocalDateTime todayStart,
		@Param("todayEnd") LocalDateTime todayEnd);

	/**
	 * 현재 예매 가능하고, 상태가 ON_SALE인 모든 콘서트의 ID 목록을 조회합니다.
	 * 스케줄러가 대기열을 처리할 대상을 찾기 위해 사용됩니다.
	 * @param status 조회할 콘서트 상태 (ConcertStatus.ON_SALE)
	 * @return 콘서트 ID 리스트
	 */
	@Query("SELECT c.concertId FROM Concert c WHERE c.status = :status")
	List<Long> findConcertIdsByStatus(ConcertStatus status);
}