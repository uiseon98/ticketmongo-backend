package com.team03.ticketmon.seat.service;

import com.team03.ticketmon._global.exception.BusinessException;
import com.team03.ticketmon._global.exception.ErrorCode;
import com.team03.ticketmon.concert.domain.Concert;
import com.team03.ticketmon.concert.domain.ConcertSeat;
import com.team03.ticketmon.concert.repository.ConcertRepository;
import com.team03.ticketmon.concert.repository.ConcertSeatRepository;
import com.team03.ticketmon.seat.dto.SeatDetailResponse;
import com.team03.ticketmon.seat.dto.SeatLayoutResponse;
import com.team03.ticketmon.seat.dto.SectionLayoutResponse;
import com.team03.ticketmon.venue.dto.VenueDTO;
import com.team03.ticketmon.venue.service.VenueService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * 좌석 배치도 관련 비즈니스 로직 서비스
 * 기존 VenueService, ConcertSeatRepository를 활용하여
 * 실제 DB 데이터 기반의 좌석 배치도 정보를 제공
 */
@Slf4j
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class SeatLayoutService {

    private final ConcertRepository concertRepository;
    private final ConcertSeatRepository concertSeatRepository;
    private final VenueService venueService;

    /**
     * 콘서트의 전체 좌석 배치도 조회
     *
     * @param concertId 콘서트 ID
     * @return 좌석 배치도 정보
     * @throws BusinessException 콘서트를 찾을 수 없는 경우
     */
    public SeatLayoutResponse getSeatLayout(Long concertId) {
        log.info("좌석 배치도 조회 시작: concertId={}", concertId);

        try {
            // 1. 콘서트 존재 여부 확인
            Concert concert = concertRepository.findById(concertId)
                    .orElseThrow(() -> new BusinessException(ErrorCode.CONCERT_NOT_FOUND));

            // 2. 공연장 정보 조회
            VenueDTO venue = venueService.getVenue(Long.valueOf(concert.getVenueName())); // ⚠️ 임시로 작성한 부분 - 반드시 수정 필요!
            SeatLayoutResponse.VenueInfo venueInfo = SeatLayoutResponse.VenueInfo.from(venue);

            // 3. 콘서트의 모든 좌석 정보 조회 (Fetch Join으로 최적화됨)
            List<ConcertSeat> concertSeats = concertSeatRepository.findByConcertIdWithDetails(concertId);

            if (concertSeats.isEmpty()) {
                log.warn("콘서트에 좌석 정보가 없습니다: concertId={}", concertId);
                // 빈 좌석 배치도 반환
                return SeatLayoutResponse.from(concertId, venueInfo, List.of());
            }

            // 4. 좌석 정보를 DTO로 변환
            List<SeatDetailResponse> seatDetails = concertSeats.stream()
                    .map(SeatDetailResponse::from)
                    .collect(Collectors.toList());

            // 5. 구역별로 그룹핑
            Map<String, List<SeatDetailResponse>> seatsBySection = seatDetails.stream()
                    .collect(Collectors.groupingBy(
                            SeatDetailResponse::section,
                            Collectors.toList()
                    ));

            // 6. 구역별 응답 생성 (구역명 기준 정렬)
            List<SectionLayoutResponse> sections = seatsBySection.entrySet().stream()
                    .sorted(Map.Entry.comparingByKey()) // 구역명으로 정렬 (A, B, C, VIP 등)
                    .map(entry -> SectionLayoutResponse.from(entry.getKey(), entry.getValue()))
                    .collect(Collectors.toList());

            // 7. 최종 응답 생성
            SeatLayoutResponse response = SeatLayoutResponse.from(concertId, venueInfo, sections);

            log.info("좌석 배치도 조회 완료: concertId={}, 총좌석={}, 구역수={}",
                    concertId, response.statistics().totalSeats(), sections.size());

            return response;

        } catch (BusinessException e) {
            log.error("좌석 배치도 조회 중 비즈니스 예외: concertId={}, error={}", concertId, e.getMessage());
            throw e;
        } catch (Exception e) {
            log.error("좌석 배치도 조회 중 예상치 못한 오류: concertId={}", concertId, e);
            throw new BusinessException(ErrorCode.SERVER_ERROR);
        }
    }

    /**
     * 특정 구역의 좌석 배치 조회
     *
     * @param concertId 콘서트 ID
     * @param sectionName 구역명 (A, B, VIP 등)
     * @return 해당 구역의 좌석 배치 정보
     * @throws BusinessException 콘서트나 구역을 찾을 수 없는 경우
     */
    public SectionLayoutResponse getSectionLayout(Long concertId, String sectionName) {
        log.info("구역별 좌석 배치도 조회: concertId={}, section={}", concertId, sectionName);

        try {
            // 1. 콘서트 존재 여부 확인
            if (!concertRepository.existsById(concertId)) {
                throw new BusinessException(ErrorCode.CONCERT_NOT_FOUND);
            }

            // 2. 해당 콘서트의 특정 구역 좌석만 조회
            List<ConcertSeat> concertSeats = concertSeatRepository.findByConcertIdWithDetails(concertId);

            // 3. 특정 구역 필터링
            List<SeatDetailResponse> sectionSeats = concertSeats.stream()
                    .filter(cs -> sectionName.equalsIgnoreCase(cs.getSeat().getSection()))
                    .map(SeatDetailResponse::from)
                    .collect(Collectors.toList());

            if (sectionSeats.isEmpty()) {
                log.warn("해당 구역에 좌석이 없습니다: concertId={}, section={}", concertId, sectionName);
                throw new BusinessException(ErrorCode.SEAT_NOT_FOUND);
            }

            SectionLayoutResponse response = SectionLayoutResponse.from(sectionName, sectionSeats);

            log.info("구역별 좌석 배치도 조회 완료: concertId={}, section={}, 좌석수={}",
                    concertId, sectionName, response.totalSeats());

            return response;

        } catch (BusinessException e) {
            log.error("구역별 좌석 배치도 조회 중 비즈니스 예외: concertId={}, section={}, error={}",
                    concertId, sectionName, e.getMessage());
            throw e;
        } catch (Exception e) {
            log.error("구역별 좌석 배치도 조회 중 예상치 못한 오류: concertId={}, section={}",
                    concertId, sectionName, e);
            throw new BusinessException(ErrorCode.SERVER_ERROR);
        }
    }

    /**
     * 좌석 배치도 캐시 무효화 (선택적 기능)
     * 콘서트 정보나 좌석 정보가 변경되었을 때 호출
     *
     * @param concertId 콘서트 ID
     */
    public void invalidateSeatLayoutCache(Long concertId) {
        log.info("좌석 배치도 캐시 무효화: concertId={}", concertId);
        // TODO: 향후 캐시 도입 시 구현
        // cacheManager.evict("seat-layout", concertId);
    }
}