package com.team03.ticketmon.concert.repository;

import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;

import com.team03.ticketmon.concert.domain.Booking;

public interface BookingRepository extends JpaRepository<Booking, Long> {
	Optional<Booking> findByBookingNumber(String bookingNumber);
}
