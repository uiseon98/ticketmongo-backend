package com.team03.ticketmon.concert.controller;

import com.team03.ticketmon.concert.dto.BookingDTO;
import com.team03.ticketmon.concert.service.BookingService;
import com.team03.ticketmon._global.exception.SuccessResponse;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.AllArgsConstructor;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import java.util.List;

/*
 * Booking Controller
 * 예매 관련 HTTP 요청 처리
 */

@RestController
@RequestMapping("/api/bookings")
@RequiredArgsConstructor
public class BookingController {

	private final BookingService bookingService;

	/**
	 * 예매 생성
	 */
	@PostMapping
	public ResponseEntity<SuccessResponse<BookingDTO>> createBooking(@RequestBody CreateBookingRequest request) {
		BookingDTO booking = bookingService.createBooking(
			request.getUserId(),
			request.getConcertId(),
			request.getConcertSeatIds()
		);
		return ResponseEntity.ok(SuccessResponse.of("예매가 완료되었습니다.", booking));
	}

	/**
	 * 사용자 예매 내역 조회
	 */
	@GetMapping("/user/{userId}")
	public ResponseEntity<SuccessResponse<List<BookingDTO>>> getUserBookings(@PathVariable Long userId) {
		List<BookingDTO> bookings = bookingService.getUserBookings(userId);
		return ResponseEntity.ok(SuccessResponse.of(bookings));
	}

	/**
	 * 예매 생성 요청 객체
	 */
	@Data
	@NoArgsConstructor
	@AllArgsConstructor
	public static class CreateBookingRequest {
		private Long userId;
		private Long concertId;
		private List<Long> concertSeatIds;
	}
}
