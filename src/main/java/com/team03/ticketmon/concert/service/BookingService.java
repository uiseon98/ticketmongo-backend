package com.team03.ticketmon.concert.service;

import com.team03.ticketmon.concert.dto.BookingDTO;
import com.team03.ticketmon.concert.dto.TicketDTO;
import com.team03.ticketmon.concert.domain.*;
import com.team03.ticketmon.concert.repository.*;
import com.team03.ticketmon._global.exception.BusinessException;
import com.team03.ticketmon._global.exception.ErrorCode;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

/*
 * Booking Service
 * 예매 비즈니스 로직 처리
 */

@Service
@RequiredArgsConstructor
@Transactional
public class BookingService {

	private final BookingRepository bookingRepository;
	private final TicketRepository ticketRepository;
	private final ConcertSeatRepository concertSeatRepository;
	private final ConcertRepository concertRepository;

	/**
	 * 예매 생성
	 */
	public BookingDTO createBooking(Long userId, Long concertId, List<Long> concertSeatIds) {
		Concert concert = concertRepository.findById(concertId)
			.orElseThrow(() -> new BusinessException(ErrorCode.RESOURCE_NOT_FOUND));

		List<ConcertSeat> concertSeats = concertSeatRepository.findAllById(concertSeatIds);

		if (concertSeats.size() != concertSeatIds.size()) {
			throw new BusinessException(ErrorCode.RESOURCE_NOT_FOUND);
		}

		BigDecimal totalAmount = concertSeats.stream()
			.map(ConcertSeat::getPrice)
			.reduce(BigDecimal.ZERO, BigDecimal::add);

		Booking booking = new Booking();
		booking.setUserId(userId);
		booking.setConcert(concert);
		booking.setBookingNumber(generateBookingNumber());
		booking.setTotalAmount(totalAmount);
		booking.setStatus("CONFIRMED");

		final Booking savedBooking = bookingRepository.save(booking);

		List<Ticket> tickets = concertSeats.stream()
			.map(concertSeat -> {
				Ticket ticket = new Ticket();
				ticket.setBooking(savedBooking);
				ticket.setConcertSeat(concertSeat);
				ticket.setTicketNumber(generateTicketNumber());
				ticket.setPrice(concertSeat.getPrice());
				return ticket;
			})
			.collect(Collectors.toList());

		ticketRepository.saveAll(tickets);

		return convertToBookingDTO(booking, tickets);
	}

	/**
	 * 사용자 예매 내역 조회
	 */
	@Transactional(readOnly = true)
	public List<BookingDTO> getUserBookings(Long userId) {
		List<Booking> bookings = bookingRepository.findByUserIdOrderByBookingIdDesc(userId);
		return bookings.stream()
			.map(booking -> {
				List<Ticket> tickets = ticketRepository.findByBookingBookingId(booking.getBookingId());
				return convertToBookingDTO(booking, tickets);
			})
			.collect(Collectors.toList());
	}

	/**
	 * 예매 번호 생성
	 */
	private String generateBookingNumber() {
		return "BK" + System.currentTimeMillis();
	}

	/**
	 * 티켓 번호 생성
	 */
	private String generateTicketNumber() {
		return "TK" + UUID.randomUUID().toString().substring(0, 8).toUpperCase();
	}

	/**
	 * Booking Entity를 DTO로 변환
	 */
	private BookingDTO convertToBookingDTO(Booking booking, List<Ticket> tickets) {
		List<TicketDTO> ticketDTOs = tickets.stream()
			.map(this::convertToTicketDTO)
			.collect(Collectors.toList());

		return new BookingDTO(
			booking.getBookingId(),
			booking.getUserId(),
			booking.getConcert().getConcertId(),
			booking.getBookingNumber(),
			booking.getTotalAmount(),
			booking.getStatus(),
			ticketDTOs
		);
	}

	/**
	 * Ticket Entity를 DTO로 변환
	 */
	private TicketDTO convertToTicketDTO(Ticket ticket) {
		ConcertSeat concertSeat = ticket.getConcertSeat();
		Seat seat = concertSeat.getSeat();

		return new TicketDTO(
			ticket.getTicketId(),
			ticket.getBooking().getBookingId(),
			concertSeat.getConcertSeatId(),
			ticket.getTicketNumber(),
			ticket.getPrice(),
			seat.getSection(),
			seat.getSeatRow(),
			seat.getSeatNumber(),
			concertSeat.getGrade()
		);
	}
}