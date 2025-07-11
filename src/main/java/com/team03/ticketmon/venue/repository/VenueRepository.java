package com.team03.ticketmon.venue.repository;

import com.team03.ticketmon.venue.domain.Venue;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

/**
 * Venue 엔티티에 대한 데이터 접근을 처리하는 Spring Data JPA 리포지토리
 */
@Repository
public interface VenueRepository extends JpaRepository<Venue, Long> {

    /**
     * 공연장 이름으로 정확히 일치하는 공연장 조회
     * SeatLayoutService에서 Concert의 venueName을 통해 Venue 정보를 찾기 위해 사용
     *
     * @param name 공연장 이름 (예: "올림픽공원 체조경기장")
     * @return 일치하는 공연장 정보 (Optional)
     */
    @Query("SELECT v FROM Venue v WHERE v.name = :name")
    Optional<Venue> findByName(@Param("name") String name);

    /**
     * 공연장 이름에 특정 키워드가 포함된 공연장들 조회
     * 검색 기능이나 유사한 이름의 공연장 찾기에 활용
     *
     * @param keyword 검색할 키워드
     * @return 키워드가 포함된 공연장 목록
     */
    @Query("SELECT v FROM Venue v WHERE LOWER(v.name) LIKE LOWER(CONCAT('%', :keyword, '%')) ORDER BY v.name")
    List<Venue> findByNameContaining(@Param("keyword") String keyword);

    /**
     * 수용 인원이 특정 범위 내에 있는 공연장들 조회
     * 콘서트 규모에 맞는 공연장 찾기에 활용
     *
     * @param minCapacity 최소 수용 인원
     * @param maxCapacity 최대 수용 인원
     * @return 수용 인원이 범위 내에 있는 공연장 목록
     */
    @Query("SELECT v FROM Venue v WHERE v.capacity BETWEEN :minCapacity AND :maxCapacity ORDER BY v.capacity")
    List<Venue> findByCapacityBetween(@Param("minCapacity") Integer minCapacity,
                                      @Param("maxCapacity") Integer maxCapacity);

    /**
     * 공연장 존재 여부 확인 (이름 기준)
     * 데이터 검증이나 중복 체크에 활용
     *
     * @param name 공연장 이름
     * @return 존재 여부
     */
    boolean existsByName(String name);
}