package com.team03.ticketmon.concert.repository;

import com.team03.ticketmon.concert.domain.Ticket;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import java.util.List;

/**
 * Ticket Repository
 * 티켓 데이터 접근 계층
 */

@Repository
public interface TicketRepository extends JpaRepository<Ticket, Long> {
	List<Ticket> findByBookingBookingId(Long bookingId);
}

