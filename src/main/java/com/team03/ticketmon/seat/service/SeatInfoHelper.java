package com.team03.ticketmon.seat.service;

import com.team03.ticketmon._global.exception.BusinessException;
import com.team03.ticketmon._global.exception.ErrorCode;
import com.team03.ticketmon.concert.domain.ConcertSeat;
import com.team03.ticketmon.concert.repository.ConcertSeatRepository;
import com.team03.ticketmon.venue.domain.Seat;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Optional;

/**
 * 좌석 정보 헬퍼 서비스
 * 기존 더미 데이터 생성 로직을 실제 DB 조회로 대체하기 위한 헬퍼 서비스
 * SeatReservationController에서 사용할 수 있도록 설계
 */
@Slf4j
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class SeatInfoHelper {

    private final ConcertSeatRepository concertSeatRepository;

    /**
     * 실제 DB에서 좌석 정보 문자열 조회
     * 기존 generateSeatInfo() 메서드를 대체
     *
     * @param concertId 콘서트 ID
     * @param seatId 좌석 ID
     * @return 좌석 정보 문자열 (예: "A-1-15")
     * @throws BusinessException 좌석을 찾을 수 없는 경우
     */
    public String getSeatInfoFromDB(Long concertId, Long seatId) {
        try {
            // ConcertSeat을 통해 실제 좌석 정보 조회
            Optional<ConcertSeat> concertSeatOpt = concertSeatRepository
                    .findByConcertIdWithDetails(concertId)
                    .stream()
                    .filter(cs -> cs.getSeat().getSeatId().equals(seatId))
                    .findFirst();

            if (concertSeatOpt.isEmpty()) {
                log.warn("좌석 정보를 찾을 수 없음: concertId={}, seatId={}", concertId, seatId);
                throw new BusinessException(ErrorCode.SEAT_NOT_FOUND);
            }

            ConcertSeat concertSeat = concertSeatOpt.get();
            Seat seat = concertSeat.getSeat();

            // 좌석 정보 문자열 생성 (구역-열-번호 형식)
            String seatInfo = String.format("%s-%s-%d",
                    seat.getSection(),
                    seat.getSeatRow(),
                    seat.getSeatNumber());

            log.debug("좌석 정보 조회 성공: concertId={}, seatId={}, seatInfo={}",
                    concertId, seatId, seatInfo);

            return seatInfo;

        } catch (BusinessException e) {
            throw e;
        } catch (Exception e) {
            log.error("좌석 정보 조회 중 예상치 못한 오류: concertId={}, seatId={}", concertId, seatId, e);
            throw new BusinessException(ErrorCode.SERVER_ERROR);
        }
    }

    /**
     * 좌석 존재 여부 확인
     *
     * @param concertId 콘서트 ID
     * @param seatId 좌석 ID
     * @return 좌석 존재 여부
     */
    public boolean seatExists(Long concertId, Long seatId) {
        try {
            return concertSeatRepository.findByConcertIdWithDetails(concertId)
                    .stream()
                    .anyMatch(cs -> cs.getSeat().getSeatId().equals(seatId));
        } catch (Exception e) {
            log.error("좌석 존재 여부 확인 중 오류: concertId={}, seatId={}", concertId, seatId, e);
            return false;
        }
    }

    /**
     * 하위 호환성을 위한 더미 데이터 생성 (임시용)
     * 실제 DB에 데이터가 없을 때 폴백으로 사용
     *
     * @param seatNumber 좌석 번호 (1부터 시작)
     * @return 더미 좌석 정보 문자열
     */
    @Deprecated
    public String generateDummySeatInfo(int seatNumber) {
        log.warn("더미 좌석 정보 생성 사용됨 (deprecated): seatNumber={}", seatNumber);

        // 기존 로직 유지 (하위 호환성)
        final int SEATS_PER_SECTION = 50;

        if (seatNumber < 1 || seatNumber > 150) {
            throw new IllegalArgumentException("유효하지 않은 좌석 번호: " + seatNumber);
        }

        String section;
        int seatInSection;

        if (seatNumber <= SEATS_PER_SECTION) {
            section = "A";
            seatInSection = seatNumber;
        } else if (seatNumber <= SEATS_PER_SECTION * 2) {
            section = "B";
            seatInSection = seatNumber - SEATS_PER_SECTION;
        } else {
            section = "C";
            seatInSection = seatNumber - (SEATS_PER_SECTION * 2);
        }

        return String.format("%s-%d", section, seatInSection);
    }
}