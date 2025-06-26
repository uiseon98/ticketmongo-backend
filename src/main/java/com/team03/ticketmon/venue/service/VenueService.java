package com.team03.ticketmon.venue.service;

import com.team03.ticketmon._global.exception.BusinessException;
import com.team03.ticketmon._global.exception.ErrorCode;
import com.team03.ticketmon.venue.domain.Venue;
import com.team03.ticketmon.venue.dto.VenueDTO;
import com.team03.ticketmon.venue.repository.VenueRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.stream.Collectors;

/**
 * 공연장 관련 비즈니스 로직을 처리하는 서비스 클래스
 * 현재 시스템에서는 주로 다른 서비스에서 공연장 정보를 조회하는 역할을 담당
 */
@Service
@Slf4j
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class VenueService {

    private final VenueRepository venueRepository;


    /**
     * 전체 공연장 정보를 조회
     *
     * @return 조회된 Venue 리스트
     */
    public List<VenueDTO> getAllVenues() {
        return venueRepository.findAll().stream()
                .map(VenueDTO::new)
                .collect(Collectors.toList());
    }

    /**
     * ID를 이용해 공연장 정보를 조회
     *
     * @param venueId 조회할 공연장의 ID
     * @return 조회된 Venue 엔티티
     * @throws BusinessException 해당 ID의 공연장이 없을 경우
     */
    public VenueDTO getVenue(Long venueId) {
        Venue venue = venueRepository.findById(venueId)
                .orElseThrow(() -> new BusinessException(ErrorCode.VENUE_NOT_FOUND));
        return new VenueDTO(venue);
    }

    /**
     * ✨ 공연장 이름으로 공연장 정보를 조회 (SeatLayoutService용 새 메서드)
     * Concert 엔티티의 venueName 필드를 통해 실제 Venue 정보를 조회하기 위해 추가
     *
     * @param venueName 공연장 이름 (예: "올림픽공원 체조경기장")
     * @return 조회된 Venue DTO
     * @throws BusinessException 해당 이름의 공연장이 없을 경우
     */
    @Cacheable(value = "venue-by-name", key = "#venueName")
    public VenueDTO getVenueByName(String venueName) {
        log.debug("공연장 이름으로 조회 시작: venueName={} (캐시 미스)", venueName);

        // 입력값 검증
        if (venueName == null || venueName.trim().isEmpty()) {
            log.warn("공연장 이름이 비어있음");
            throw new BusinessException(ErrorCode.INVALID_INPUT, "공연장 이름을 입력해주세요.");
        }

        String trimmedVenueName = venueName.trim();

        Venue venue = venueRepository.findByName(trimmedVenueName)
                .orElseThrow(() -> {
                    log.warn("공연장을 찾을 수 없음: venueName={}", trimmedVenueName);

                    // 🔧 유사한 이름의 공연장이 있는지 확인 (사용자 친화적 에러 메시지)
                    List<Venue> similarVenues = venueRepository.findByNameContaining(trimmedVenueName);
                    if (!similarVenues.isEmpty()) {
                        log.info("유사한 공연장 발견: count={}, examples={}",
                                similarVenues.size(),
                                similarVenues.stream().limit(3).map(Venue::getName).collect(Collectors.toList()));

                        // 가장 유사한 공연장명 제안
                        String suggestions = similarVenues.stream()
                                .limit(3)
                                .map(Venue::getName)
                                .collect(Collectors.joining(", "));

                        return new BusinessException(ErrorCode.VENUE_NOT_FOUND,
                                String.format("'%s' 공연장을 찾을 수 없습니다. 유사한 공연장: %s",
                                        trimmedVenueName, suggestions));
                    }

                    return new BusinessException(ErrorCode.VENUE_NOT_FOUND,
                            String.format("'%s' 공연장을 찾을 수 없습니다.", trimmedVenueName));
                });

        log.debug("공연장 이름으로 조회 성공: venueName={}, venueId={}, capacity={} (캐시 저장)",
                trimmedVenueName, venue.getVenueId(), venue.getCapacity());

        return new VenueDTO(venue);
    }

    /**
     * 공연장 이름 검색 (키워드 기반)
     * 관리자나 사용자가 공연장을 검색할 때 사용
     *
     * @param keyword 검색할 키워드
     * @return 키워드가 포함된 공연장 목록
     */
    public List<VenueDTO> searchVenuesByKeyword(String keyword) {
        log.debug("공연장 키워드 검색 시작: keyword={}", keyword);

        if (keyword == null || keyword.trim().isEmpty()) {
            log.debug("키워드가 비어있어 전체 목록 반환");
            return getAllVenues();
        }

        String trimmedKeyword = keyword.trim();
        List<VenueDTO> venues = venueRepository.findByNameContaining(trimmedKeyword).stream()
                .map(VenueDTO::new)
                .collect(Collectors.toList());

        log.debug("공연장 키워드 검색 완료: keyword={}, 결과수={}", trimmedKeyword, venues.size());
        return venues;
    }

    /**
     * 수용 인원 범위로 공연장 조회
     * 콘서트 규모에 맞는 공연장을 찾을 때 사용
     *
     * @param minCapacity 최소 수용 인원
     * @param maxCapacity 최대 수용 인원
     * @return 조건에 맞는 공연장 목록
     */
    public List<VenueDTO> getVenuesByCapacityRange(Integer minCapacity, Integer maxCapacity) {
        log.debug("수용 인원 범위로 공연장 조회: min={}, max={}", minCapacity, maxCapacity);

        // 기본값 설정
        int min = minCapacity != null && minCapacity > 0 ? minCapacity : 1;
        int max = maxCapacity != null && maxCapacity > min ? maxCapacity : Integer.MAX_VALUE;

        List<VenueDTO> venues = venueRepository.findByCapacityBetween(min, max).stream()
                .map(VenueDTO::new)
                .collect(Collectors.toList());

        log.debug("수용 인원 범위 조회 완료: min={}, max={}, 결과수={}", min, max, venues.size());
        return venues;
    }

    /**
     * 🔧 캐시 무효화 - 공연장 정보 변경 시 호출
     * 관리자가 공연장 정보를 수정/삭제할 때 사용
     */
    @CacheEvict(value = {"venues", "venue-by-id", "venue-by-name"}, allEntries = true)
    public void evictVenueCache() {
        log.info("모든 공연장 캐시 무효화 완료");
    }

    /**
     * 특정 공연장 캐시만 무효화
     */
    @CacheEvict(value = {"venue-by-id"}, key = "#venueId")
    public void evictVenueCache(Long venueId) {
        log.info("특정 공연장 캐시 무효화: venueId={}", venueId);
    }
}