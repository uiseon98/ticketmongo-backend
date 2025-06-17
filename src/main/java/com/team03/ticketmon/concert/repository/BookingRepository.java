package com.team03.ticketmon.concert.repository;

import com.team03.ticketmon.concert.domain.Booking;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import java.util.List;

/*
 * Booking Repository
 * 예매 데이터 접근 계층
 */

@Repository
public interface BookingRepository extends JpaRepository<Booking, Long> {
	List<Booking> findByUserIdOrderByBookingIdDesc(Long userId);
}