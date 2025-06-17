package com.team03.ticketmon.concert.dto;

import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.AllArgsConstructor;
import java.math.BigDecimal;
import java.util.List;

/*
 * Booking DTO
 * 예매 정보 전송 객체
 */

@Data
@NoArgsConstructor
@AllArgsConstructor
public class BookingDTO {
	private Long bookingId;
	private Long userId;
	private Long concertId;
	private String bookingNumber;
	private BigDecimal totalAmount;
	private String status;
	private List<TicketDTO> tickets;
}

