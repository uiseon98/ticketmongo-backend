package com.team03.ticketmon.concert.scheduler;

import com.team03.ticketmon.concert.domain.Concert;
import com.team03.ticketmon.concert.domain.enums.ConcertStatus;
import com.team03.ticketmon.concert.repository.ConcertRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.Arrays;
import java.util.List;

/**
 * 콘서트 상태 자동 업데이트 스케줄러
 * 매 시간마다 실행되어 콘서트 상태를 체크하고 필요시 업데이트
 */
@Component
@Slf4j
@RequiredArgsConstructor
public class ConcertStatusScheduler {

	private final ConcertRepository concertRepository;

	/**
	 * 매 시간마다 콘서트 상태 업데이트
	 * SCHEDULED → ON_SALE, ON_SALE/SOLD_OUT → COMPLETED 자동 변경
	 * SOLD_OUT 상태는 시간 기반 변경(COMPLETED)만 수행
	 */
	@Scheduled(fixedRate = 3600000) // 1시간마다 실행 (3600000ms = 1시간)
	@Transactional
	public void updateConcertStatuses() {
		log.info("콘서트 상태 자동 업데이트 스케줄러 시작");

		try {
			// 자동 상태 변경 대상 상태들
			List<ConcertStatus> targetStatuses = Arrays.asList(
				ConcertStatus.SCHEDULED,
				ConcertStatus.ON_SALE,
				ConcertStatus.SOLD_OUT
			);

			// 대상 상태의 모든 콘서트 조회
			List<Concert> concerts = concertRepository.findByStatusInOrderByConcertDateAsc(targetStatuses);

			int updatedCount = 0;

			for (Concert concert : concerts) {
				// 현재 상태 저장
				ConcertStatus oldStatus = concert.getStatus();
				ConcertStatus newStatus = oldStatus;

				if (oldStatus == ConcertStatus.SOLD_OUT) {
					// SOLD_OUT 상태는 시간 기반 변경만 체크 (COMPLETED 여부)
					if (shouldCompleteByTime(concert)) {
						newStatus = ConcertStatus.COMPLETED;
					}
				} else {
					// SCHEDULED, ON_SALE 상태는 일반 로직 적용
					newStatus = concert.determineCurrentStatus(false);
				}

				// 상태가 변경되었으면 업데이트
				if (!oldStatus.equals(newStatus)) {
					concert.setStatus(newStatus);
					concertRepository.save(concert);
					updatedCount++;

					log.info("콘서트 상태 변경: ID={}, 제목='{}', {} → {}",
						concert.getConcertId(),
						concert.getTitle(),
						oldStatus,
						newStatus);
				}
			}

			log.info("콘서트 상태 자동 업데이트 완료: 총 {}개 콘서트 중 {}개 상태 변경",
				concerts.size(), updatedCount);

		} catch (Exception e) {
			log.error("콘서트 상태 자동 업데이트 중 오류 발생", e);
		}
	}

	/**
	 * 시간 기준으로 COMPLETED 상태로 변경해야 하는지 체크
	 */
	private boolean shouldCompleteByTime(Concert concert) {
		if (concert.getConcertDate() == null || concert.getStartTime() == null) {
			return false;
		}

		LocalDateTime now = LocalDateTime.now();
		LocalDateTime concertStartDateTime = concert.getConcertDate().atTime(concert.getStartTime());
		LocalDateTime ticketCloseDateTime = concertStartDateTime.minusMinutes(30);

		return now.isAfter(ticketCloseDateTime) || now.isEqual(ticketCloseDateTime);
	}
}