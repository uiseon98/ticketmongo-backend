package com.team03.ticketmon.payment.dto;

import com.team03.ticketmon.concert.domain.Booking;

import lombok.Getter;

@Getter
public class BookingResponseDto {
	private String bookingNumber;
	private String concertTitle;
	private Double amount;

	public BookingResponseDto(Booking booking) {
		this.bookingNumber = booking.getBookingNumber();
		this.concertTitle = booking.getConcert().getTitle(); // 예시, 실제 필드에 맞게 수정
		this.amount = booking.getTotalAmount().doubleValue();
	}
}
