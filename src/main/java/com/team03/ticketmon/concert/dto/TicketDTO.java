package com.team03.ticketmon.concert.dto;

import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.AllArgsConstructor;
import java.math.BigDecimal;

/*
 * Ticket DTO
 * 티켓 정보 전송 객체
 */

@Data
@NoArgsConstructor
@AllArgsConstructor
public class TicketDTO {
	private Long ticketId;
	private Long bookingId;
	private Long concertSeatId;
	private String ticketNumber;
	private BigDecimal price;
	private String section;
	private String seatRow;
	private Integer seatNumber;
	private String grade;
}

