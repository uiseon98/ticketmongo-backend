package com.team03.ticketmon.booking.facade;

import com.team03.ticketmon.booking.domain.Booking;
import com.team03.ticketmon.booking.dto.BookingCreateRequest;
import com.team03.ticketmon.booking.service.BookingService;
import com.team03.ticketmon.payment.dto.PaymentCancelRequest;
import com.team03.ticketmon.payment.dto.PaymentExecutionResponse;
import com.team03.ticketmon.payment.service.PaymentService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;

/**
 * 예매‑결제 유즈케이스를 하나의 트랜잭션으로 오케스트레이션
 * 하나의 유스케이스를 위한 트랜잭션 단위를 정의
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class BookingFacadeService {

    private final BookingService bookingService;
    private final PaymentService paymentService;

    /**
     * 좌석 선점 검증 →  PENDING 예매 생성 →  결제 정보(Payment) 생성·반환
     *
     * @param createRequest 콘서트 ID, 콘서트좌석 IDs
     * @param userId        예매를 요청한 사용자 ID
     */
    @Transactional
    public PaymentExecutionResponse createBookingAndInitiatePayment(BookingCreateRequest createRequest, Long userId) {

        // 1. 예매 생성(PENDING_PAYMENT 상태)
        Booking pendingBooking = bookingService.createPendingBooking(createRequest, userId);

        // 2. PaymentService에 위임해서 “(PENDING)” 객체를 찾거나 생성
        return paymentService.initiatePayment(pendingBooking, userId);
    }

    /**
     * 신규 비동기(non-blocking) 취소 메서드
     */
    public Mono<Void> cancelBookingAndPayment(Long bookingId, Long userId) {
        return Mono.fromCallable(() ->
                        // 1) 취소 가능 여부 검증 (블로킹 ⇒ boundedElastic 풀에서)
                        bookingService.validateCancellableBooking(bookingId, userId)
                )
                .subscribeOn(Schedulers.boundedElastic())
                .flatMap(booking ->
                        // 1) payment 취소
                        paymentService.cancelPayment(
                                        booking,
                                        new PaymentCancelRequest("사용자 예매 취소"),
                                        userId
                                )
                                // 3) 취소 후 내부 DB 상태 반영(finalizeCancellation)
                                .then(
                                        Mono.fromRunnable(() ->
                                                        bookingService.finalizeCancellation(booking.getBookingId())
                                                )
                                                .subscribeOn(Schedulers.boundedElastic())
                                                .onErrorResume(error -> {
                                                    log.error("예약 취소 finalization 실패, 보상 트랜잭션 필요: {}", error.getMessage());
                                                    // TODO: 결제 취소 롤백 또는 보상 로직
                                                    return Mono.error(error);
                                                })
                                )
                )
                .then(); // Mono<Void> 반환
    }
}
