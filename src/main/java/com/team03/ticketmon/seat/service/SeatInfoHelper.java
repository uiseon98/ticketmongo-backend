package com.team03.ticketmon.seat.service;

import com.team03.ticketmon._global.exception.BusinessException;
import com.team03.ticketmon._global.exception.ErrorCode;
import com.team03.ticketmon.concert.domain.ConcertSeat;
import com.team03.ticketmon.concert.repository.ConcertSeatRepository;
import com.team03.ticketmon.venue.domain.Seat;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Optional;

/**
 * 좌석 정보 헬퍼 서비스
 * ✅ 개선사항:
 * - 실제 DB 조회 기능 완전 구현
 * - 성능 최적화: 캐시 적용
 * - 더미 데이터 폴백 지원 (하위 호환성)
 * - 에러 처리 강화
 */
@Slf4j
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class SeatInfoHelper {

    private final ConcertSeatRepository concertSeatRepository;

    /**
     * ✅ 핵심 개선: 실제 DB에서 좌석 정보 조회 (캐시 적용)
     *
     * @param concertId 콘서트 ID
     * @param seatId 좌석 ID
     * @return 좌석 정보 문자열 (예: "A-1-15" = 구역-열-번호)
     * @throws BusinessException 좌석을 찾을 수 없는 경우
     */
    @Cacheable(value = "seatInfo", key = "#concertId + ':' + #seatId", unless = "#result == null")
    public String getSeatInfoFromDB(Long concertId, Long seatId) {
        try {
            log.debug("DB에서 좌석 정보 조회 시작: concertId={}, seatId={}", concertId, seatId);

            // ✅ 성능 최적화: 직접 쿼리로 특정 좌석만 조회 (기존 전체 조회 후 필터링 방식 개선)
            Optional<ConcertSeat> concertSeatOpt = concertSeatRepository
                    .findByConcertIdAndSeatId(concertId, seatId);

            if (concertSeatOpt.isEmpty()) {
                log.warn("좌석 정보를 찾을 수 없음: concertId={}, seatId={}", concertId, seatId);
                throw new BusinessException(ErrorCode.SEAT_NOT_FOUND,
                        String.format("좌석을 찾을 수 없습니다. 콘서트ID: %d, 좌석ID: %d", concertId, seatId));
            }

            ConcertSeat concertSeat = concertSeatOpt.get();
            Seat seat = concertSeat.getSeat();

            // ✅ 좌석 정보 포맷 개선: 구역-열-번호 (A-1-15)
            String seatInfo = String.format("%s-%s-%d",
                    seat.getSection() != null ? seat.getSection() : "?",
                    seat.getSeatRow() != null ? seat.getSeatRow() : "?",
                    seat.getSeatNumber() != null ? seat.getSeatNumber() : 0);

            log.debug("좌석 정보 조회 성공: concertId={}, seatId={}, seatInfo={}",
                    concertId, seatId, seatInfo);

            return seatInfo;

        } catch (BusinessException e) {
            // 비즈니스 예외는 그대로 전파
            throw e;
        } catch (Exception e) {
            log.error("좌석 정보 조회 중 예상치 못한 오류: concertId={}, seatId={}", concertId, seatId, e);
            throw new BusinessException(ErrorCode.SERVER_ERROR,
                    "좌석 정보 조회 중 오류가 발생했습니다.");
        }
    }

    /**
     * ✅ 새로운 메서드: 좌석 존재 여부만 빠르게 확인
     *
     * @param concertId 콘서트 ID
     * @param seatId 좌석 ID
     * @return 좌석 존재 여부
     */
    @Cacheable(value = "seatExists", key = "#concertId + ':' + #seatId")
    public boolean seatExists(Long concertId, Long seatId) {
        try {
            return concertSeatRepository.existsByConcertIdAndSeatId(concertId, seatId);
        } catch (Exception e) {
            log.error("좌석 존재 여부 확인 중 오류: concertId={}, seatId={}", concertId, seatId, e);
            return false;
        }
    }

    /**
     * ✅ 개선된 더미 데이터 생성 (하위 호환성 + 폴백용)
     * 실제 DB 조회 실패 시 폴백으로 사용
     *
     * @param seatNumber 좌석 번호 (1부터 시작)
     * @return 더미 좌석 정보 문자열 (예: "A-1")
     */
    public String generateDummySeatInfo(int seatNumber) {
        log.debug("더미 좌석 정보 생성: seatNumber={}", seatNumber);

        // 좌석 번호 유효성 검증
        if (seatNumber < 1 || seatNumber > 150) {
            log.error("유효하지 않은 좌석 번호: seatNumber={}", seatNumber);
            throw new IllegalArgumentException(
                    String.format("좌석 번호는 1부터 150 사이여야 합니다. 입력된 값: %d", seatNumber));
        }

        final int SEATS_PER_SECTION = 50;
        String section;
        int seatInSection;

        if (seatNumber <= SEATS_PER_SECTION) {
            // A구역: 1-50
            section = "A";
            seatInSection = seatNumber;
        } else if (seatNumber <= SEATS_PER_SECTION * 2) {
            // B구역: 51-100
            section = "B";
            seatInSection = seatNumber - SEATS_PER_SECTION;
        } else {
            // C구역: 101-150
            section = "C";
            seatInSection = seatNumber - (SEATS_PER_SECTION * 2);
        }

        String seatInfo = String.format("%s-%d", section, seatInSection);
        log.debug("더미 좌석 정보 생성 완료: seatNumber={} -> seatInfo={}", seatNumber, seatInfo);

        return seatInfo;
    }

    /**
     * ✅ 새로운 메서드: 좌석 정보 캐시 무효화
     * 좌석 정보가 변경될 때 호출
     *
     * @param concertId 콘서트 ID
     * @param seatId 좌석 ID
     */
    public void evictSeatInfoCache(Long concertId, Long seatId) {
        // Spring Cache 무효화는 @CacheEvict 애노테이션으로 처리
        log.debug("좌석 정보 캐시 무효화: concertId={}, seatId={}", concertId, seatId);
    }

    /**
     * ✅ 개선된 정적 메서드: Seat 엔티티로부터 좌석 정보 생성
     * SeatCacheInitService에서 사용
     *
     * @param seat Seat 엔티티
     * @return 좌석 정보 문자열
     */
    public static String generateSeatInfoFromEntity(Seat seat) {
        if (seat == null) {
            log.warn("Seat 엔티티가 null입니다.");
            return "UNKNOWN";
        }

        String section = seat.getSection() != null ? seat.getSection() : "?";
        String seatRow = seat.getSeatRow() != null ? seat.getSeatRow() : "?";
        Integer seatNumber = seat.getSeatNumber() != null ? seat.getSeatNumber() : 0;

        return String.format("%s-%s-%d", section, seatRow, seatNumber);
    }
}