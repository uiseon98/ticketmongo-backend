package com.team03.ticketmon.seat.service;

import com.team03.ticketmon.concert.domain.ConcertSeat;
import com.team03.ticketmon.concert.repository.ConcertSeatRepository;
import com.team03.ticketmon.seat.domain.SeatStatus;
import com.team03.ticketmon.seat.domain.SeatStatus.SeatStatusEnum;
import com.team03.ticketmon.venue.domain.Seat;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.redisson.api.RMap;
import org.redisson.api.RedissonClient;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 좌석 상태 캐시 초기화 서비스
 * ✅ 개선사항:
 * - SeatInfoHelper 활용으로 코드 중복 제거
 * - 성능 최적화 및 에러 처리 강화
 * - 배치 처리 개선
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class SeatCacheInitService {

    private final RedissonClient redissonClient;
    private final ConcertSeatRepository concertSeatRepository;
    private static final String SEAT_STATUS_KEY_PREFIX = "seat:status:";

    /**
     * ✅ 개선된 DB 기반 좌석 캐시 초기화
     * SeatInfoHelper.generateSeatInfoFromEntity() 활용
     */
    @Transactional(readOnly = true)
    public void initializeSeatCacheFromDB(Long concertId) {
        log.info("DB 기반 좌석 캐시 초기화 시작: concertId={}", concertId);

        try {
            // 1. DB에서 콘서트의 모든 좌석 정보 조회 (Fetch Join 적용)
            List<ConcertSeat> concertSeats = concertSeatRepository.findByConcertIdWithDetails(concertId);

            if (concertSeats.isEmpty()) {
                log.warn("콘서트 좌석 데이터가 없습니다: concertId={}", concertId);
                return;
            }

            // 2. Redis 캐시 구조 준비
            String key = SEAT_STATUS_KEY_PREFIX + concertId;
            RMap<String, SeatStatus> seatMap = redissonClient.getMap(key);

            // 기존 캐시 클리어
            seatMap.clear();

            // 3. 로컬 맵에 모든 좌석 상태 준비 (배치 처리 최적화)
            Map<String, SeatStatus> batchSeatData = new HashMap<>();
            int bookedCount = 0;

            for (ConcertSeat concertSeat : concertSeats) {
                try {
                    // 4. 좌석 정보 추출
                    Seat seat = concertSeat.getSeat();
                    Long seatId = seat.getSeatId();

                    // 5. 예매 여부 확인 (Ticket 존재 여부로 판별)
                    boolean isBooked = concertSeat.getTicket() != null;
                    SeatStatusEnum status = isBooked ? SeatStatusEnum.BOOKED : SeatStatusEnum.AVAILABLE;

                    if (isBooked) {
                        bookedCount++;
                    }

                    // 6. ✅ 핵심 개선: SeatInfoHelper 활용으로 코드 중복 제거
                    String seatInfo = SeatInfoHelper.generateSeatInfoFromEntity(seat);

                    // 7. SeatStatus 객체 생성
                    SeatStatus seatStatus = SeatStatus.builder()
                            .id(concertId + "-" + seatId)
                            .concertId(concertId)
                            .seatId(seatId)
                            .status(status)
                            .userId(null) // 초기화 시에는 선점 사용자 없음
                            .reservedAt(null)
                            .expiresAt(null)
                            .seatInfo(seatInfo)
                            .build();

                    // 8. 로컬 맵에 누적 (Redis 호출 없음)
                    batchSeatData.put(seatId.toString(), seatStatus);

                } catch (Exception e) {
                    log.error("좌석 상태 생성 중 오류: concertId={}, concertSeat={}",
                            concertId, concertSeat.getConcertSeatId(), e);
                    // 개별 좌석 오류는 스킵하고 계속 처리
                }
            }

            // 9. 한 번의 Redis 호출로 모든 데이터 일괄 저장
            if (!batchSeatData.isEmpty()) {
                seatMap.putAll(batchSeatData);

                log.info("DB 기반 좌석 캐시 초기화 완료: concertId={}, totalSeats={}, bookedSeats={}, availableSeats={}",
                        concertId, batchSeatData.size(), bookedCount, batchSeatData.size() - bookedCount);
            } else {
                log.warn("처리 가능한 좌석 데이터가 없습니다: concertId={}", concertId);
            }

        } catch (Exception e) {
            log.error("DB 기반 좌석 캐시 초기화 중 오류 발생: concertId={}", concertId, e);
            throw new RuntimeException("좌석 캐시 초기화 실패: " + e.getMessage(), e);
        }
    }

    /**
     * 더미 데이터 기반 캐시 초기화 (기존 메서드, 테스트용 유지)
     */
    public void initializeSeatCache(Long concertId, int totalSeats) {
        String key = SEAT_STATUS_KEY_PREFIX + concertId;
        RMap<String, SeatStatus> seatMap = redissonClient.getMap(key);

        // 기존 캐시 클리어
        seatMap.clear();

        // 로컬 HashMap에 모든 좌석 데이터 준비 (네트워크 호출 최소화)
        Map<String, SeatStatus> batchSeatData = new HashMap<>();

        // ✅ 개선: SeatInfoHelper 활용
        SeatInfoHelper seatInfoHelper = new SeatInfoHelper(null); // 더미용

        for (int i = 1; i <= totalSeats; i++) {
            try {
                String seatInfo = seatInfoHelper.generateDummySeatInfo(i);

                SeatStatus seatStatus = SeatStatus.builder()
                        .id(concertId + "-" + i)
                        .concertId(concertId)
                        .seatId((long) i)
                        .status(SeatStatusEnum.AVAILABLE)
                        .userId(null)
                        .reservedAt(null)
                        .expiresAt(null)
                        .seatInfo(seatInfo)
                        .build();

                batchSeatData.put(String.valueOf(i), seatStatus);
            } catch (Exception e) {
                log.error("더미 좌석 데이터 생성 오류: concertId={}, seatNumber={}", concertId, i, e);
            }
        }

        // 한 번의 Redis 호출로 모든 데이터 일괄 저장
        if (!batchSeatData.isEmpty()) {
            seatMap.putAll(batchSeatData);
        }

        log.info("더미 좌석 캐시 초기화 완료: concertId={}, totalSeats={}", concertId, batchSeatData.size());
    }

    /**
     * ✅ 개선된 캐시 상태 확인
     */
    public Map<String, Object> getCacheStatus(Long concertId) {
        String key = SEAT_STATUS_KEY_PREFIX + concertId;
        RMap<String, SeatStatus> seatMap = redissonClient.getMap(key);

        if (!seatMap.isExists()) {
            return Map.of(
                    "concertId", concertId,
                    "cacheKey", key,
                    "cacheExists", false,
                    "message", "캐시가 존재하지 않습니다."
            );
        }

        // 캐시 통계 계산
        Map<String, SeatStatus> allSeats = seatMap.readAllMap();

        long availableSeats = allSeats.values().stream()
                .mapToLong(seat -> seat.getStatus() == SeatStatusEnum.AVAILABLE ? 1 : 0)
                .sum();

        long reservedSeats = allSeats.values().stream()
                .mapToLong(seat -> seat.getStatus() == SeatStatusEnum.RESERVED ? 1 : 0)
                .sum();

        long bookedSeats = allSeats.values().stream()
                .mapToLong(seat -> seat.getStatus() == SeatStatusEnum.BOOKED ? 1 : 0)
                .sum();

        Map<String, Object> status = Map.of(
                "concertId", concertId,
                "cacheKey", key,
                "cacheExists", true,
                "totalSeats", allSeats.size(),
                "availableSeats", availableSeats,
                "reservedSeats", reservedSeats,
                "bookedSeats", bookedSeats,
                "lastUpdated", java.time.LocalDateTime.now()
        );

        log.debug("캐시 상태 조회: {}", status);
        return status;
    }

    /**
     * ✅ 개선된 캐시 삭제
     */
    public String clearSeatCache(Long concertId) {
        try {
            String key = SEAT_STATUS_KEY_PREFIX + concertId;
            RMap<String, SeatStatus> seatMap = redissonClient.getMap(key);

            // 캐시 존재 여부 확인
            if (!seatMap.isExists()) {
                log.info("삭제할 좌석 캐시가 존재하지 않음: concertId={}, key={}", concertId, key);
                return "삭제할 캐시가 없습니다.";
            }

            // 삭제 전 통계 수집
            int seatCount = seatMap.size();

            // 캐시 삭제
            boolean deleted = seatMap.delete();

            if (deleted) {
                log.info("좌석 캐시 삭제 완료: concertId={}, deletedSeats={}", concertId, seatCount);
                return String.format("좌석 캐시 삭제 성공 (삭제된 좌석 수: %d)", seatCount);
            } else {
                log.warn("좌석 캐시 삭제 실패: concertId={}", concertId);
                return "캐시 삭제에 실패했습니다.";
            }

        } catch (Exception e) {
            log.error("좌석 캐시 삭제 중 오류: concertId={}", concertId, e);
            return "캐시 삭제 중 오류가 발생했습니다: " + e.getMessage();
        }
    }
}