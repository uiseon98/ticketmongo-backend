package com.team03.ticketmon.concert.repository;

import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;

import com.team03.ticketmon.concert.domain.Booking;
import com.team03.ticketmon.concert.domain.enums.BookingStatus;

public interface BookingRepository extends JpaRepository<Booking, Long> {
	Optional<Booking> findByBookingNumber(String bookingNumber);

	List<Booking> findByUserId(Long userId);

	List<Booking> findByStatus(BookingStatus status); // 💡 [추가]

}
