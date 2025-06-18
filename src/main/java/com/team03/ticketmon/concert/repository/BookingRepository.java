package com.team03.ticketmon.concert.repository;

import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;

import com.team03.ticketmon.concert.domain.Booking;

public interface BookingRepository extends JpaRepository<Booking, Long> {
	// 예매 번호로 Booking 엔티티를 찾기 위한 쿼리 메소드
	Optional<Booking> findByBookingNumber(String bookingNumber);
}
