package com.team03.ticketmon.payment.service;

import com.team03.ticketmon._global.config.AppProperties;
import com.team03.ticketmon._global.exception.BusinessException;
import com.team03.ticketmon._global.exception.ErrorCode;
import com.team03.ticketmon.booking.domain.Booking;
import com.team03.ticketmon.booking.domain.BookingStatus;
import com.team03.ticketmon.booking.repository.BookingRepository;
import com.team03.ticketmon.payment.config.TossPaymentsProperties;
import com.team03.ticketmon.payment.domain.entity.Payment;
import com.team03.ticketmon.payment.domain.entity.PaymentCancelHistory;
import com.team03.ticketmon.payment.domain.enums.PaymentStatus;
import com.team03.ticketmon.payment.dto.PaymentCancelRequest;
import com.team03.ticketmon.payment.dto.PaymentConfirmRequest;
import com.team03.ticketmon.payment.dto.PaymentExecutionResponse;
import com.team03.ticketmon.payment.dto.PaymentHistoryDto;
import com.team03.ticketmon.payment.repository.PaymentCancelHistoryRepository;
import com.team03.ticketmon.payment.repository.PaymentRepository;
import com.team03.ticketmon.seat.service.SeatStatusService;
import com.team03.ticketmon.user.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.MediaType;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;
import reactor.util.function.Tuples;

import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.*;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class PaymentService {

    private final BookingRepository bookingRepository;
    private final PaymentRepository paymentRepository;
    private final PaymentCancelHistoryRepository paymentCancelHistoryRepository;
    private final TossPaymentsProperties tossPaymentsProperties;
    private final AppProperties appProperties;
    private final WebClient webClient;
    private final UserRepository userRepository;
    private final SeatStatusService seatStatusService;

    @Transactional
    public PaymentExecutionResponse initiatePayment(Booking booking, Long currentUserId) {
        // 예매 정보와 사용자 ID를 이용해 결제 정보를 생성 또는 조회합니다.
        // 1. 유효성 검사 (존재, 사용자 소유, 대기상태, 콘서트 연결 유무)
        if (booking == null) {
            throw new BusinessException(ErrorCode.BOOKING_NOT_FOUND);
        }
        if (!booking.getUserId().equals(currentUserId)) {
            log.warn("사용자 {}가 본인 소유가 아닌 예매(ID:{}) 결제를 시도했습니다.", currentUserId, booking.getBookingId());
            throw new AccessDeniedException("본인의 예매만 결제할 수 있습니다.");
        }
        if (booking.getStatus() != BookingStatus.PENDING_PAYMENT) {
            throw new BusinessException(ErrorCode.INVALID_BOOKING_STATUS_FOR_PAYMENT);
        }
        if (booking.getConcert() == null) {
            throw new IllegalStateException("예매에 연결된 콘서트 정보가 없습니다. Booking ID: " + booking.getBookingId());
        }

        // 2. 기존에 결제 대기(PENDING) 내역이 있으면 재활용, 아니면 새로 생성합니다
        Payment paymentToUse = paymentRepository.findByBooking(booking)
                .filter(p -> p.getStatus() == PaymentStatus.PENDING)
                .orElseGet(() -> {
                    log.info("신규 결제 정보를 생성합니다. bookingNumber: {}", booking.getBookingNumber());
                    String orderId = UUID.randomUUID().toString();
                    Payment newPayment = Payment.builder()
                            .booking(booking)
                            .userId(booking.getUserId())
                            .orderId(orderId)
                            .amount(booking.getTotalAmount())
                            .build();
                    booking.setPayment(newPayment);
                    return paymentRepository.save(newPayment);
                });

        // 3. 프론트에서 결제 진행화면에 표시할 고객명(닉네임 등) 조회
        String customerName = userRepository.findById(currentUserId)
                .map(user -> user.getNickname())
                .orElse("사용자 " + currentUserId);

        // 4. 결제 정보와 리다이렉트 정보 등 클라이언트에 반환
        return PaymentExecutionResponse.builder()
                .orderId(paymentToUse.getOrderId())
                .bookingNumber(booking.getBookingNumber())
                .orderName(booking.getConcert().getTitle())
                .amount(booking.getTotalAmount())
                .customerName(customerName)
                .clientKey(tossPaymentsProperties.clientKey())
                .successUrl(appProperties.baseUrl() + "/api/v1/payments/success")
                .failUrl(appProperties.baseUrl() + "/api/v1/payments/fail")
                .build();
    }

    @Transactional
    public void savePayment(Payment payment) {
        // 결제 정보를 DB에 저장하고, 해당 예매(Booking)을 확정 상태로 만듭니다.
        paymentRepository.save(payment);
        bookingRepository.findById(payment.getBooking().getBookingId())
                .ifPresent(b -> b.confirm());
    }

    @Transactional
    public Mono<Void> confirmPayment(PaymentConfirmRequest req) {
        // 1. 주문ID로 DB의 결제 정보 조회 및 상태, 금액 검증
        return Mono.fromCallable(() ->
                        paymentRepository.findByOrderId(req.getOrderId())
                                .orElseThrow(() -> new BusinessException(
                                        ErrorCode.RESOURCE_NOT_FOUND,
                                        "존재하지 않는 주문 ID: " + req.getOrderId()))
                )
                .subscribeOn(Schedulers.boundedElastic())
                .flatMap(payment -> {
                    // 결제 대기(PENDING) 상태만 승인 허용
                    if (payment.getStatus() != PaymentStatus.PENDING) {
                        return Mono.error(new BusinessException(
                                ErrorCode.ALREADY_PROCESSED_PAYMENT,
                                "이미 처리된 결제입니다."));
                    }
                    // 결제 금액이 실제 결제 요청금액과 일치하는지 체크
                    if (payment.getAmount().compareTo(req.getAmount()) != 0) {
                        payment.fail();
                        return Mono.error(new BusinessException(
                                ErrorCode.PAYMENT_AMOUNT_MISMATCH,
                                "결제 금액이 일치하지 않습니다."));
                    }
                    return Mono.just(payment);
                })
                .flatMap(payment -> {
                    // 2. 외부 결제사(TOSS) 결제 승인 API 호출 - HTTP 인증 포함
                    String rawKey = tossPaymentsProperties.secretKey() + ":";
                    String encodedKey = Base64.getEncoder()
                            .encodeToString(rawKey.getBytes(StandardCharsets.UTF_8));
                    return callTossConfirmApi(req, encodedKey, req.getOrderId())
                            .map(resp -> Tuples.of(payment, resp));
                })
                .flatMap(tuple -> {
                    // 3. 외부 결제 승인 응답의 상태 등 검증 및 DB 반영
                    Payment payment = tuple.getT1();
                    @SuppressWarnings("unchecked")
                    Map<String, Object> resp = (Map<String, Object>) tuple.getT2();
                    String status = (String) resp.get("status");
                    if (!"DONE".equals(status)) {
                        payment.fail();
                        return Mono.error(new BusinessException(
                                ErrorCode.PAYMENT_VALIDATION_FAILED,
                                "Toss 승인 상태가 DONE이 아닙니다: " + status));
                    }
                    LocalDateTime approvedAt = parseDateTime(resp.get("approvedAt"));
                    payment.setPaymentMethod(req.getOriginalMethod());
                    return Mono.fromRunnable(() -> {
                                payment.complete(req.getPaymentKey(), approvedAt);
                                paymentRepository.save(payment);

                                // 연관 예매(Booking) 역시 CONFIRMED로 변경
                                Long bookingId = payment.getBooking().getBookingId();
                                Booking booking = bookingRepository
                                        .findWithConcertAndTicketsById(bookingId)
                                        .orElseThrow(() -> new BusinessException(
                                                ErrorCode.RESOURCE_NOT_FOUND,
                                                "예매를 찾을 수 없습니다: " + bookingId
                                        ));
                                booking.confirm();
                                bookingRepository.save(booking);

                                // 좌석 정보도 BOOKED로 최종 변경
                                Long concertId = booking.getConcert().getConcertId();
                                List<Long> failedSeats = new ArrayList<>();
                                booking.getTickets().forEach(ticket -> {
                                    try {
                                        seatStatusService.bookSeat(
                                                concertId,
                                                ticket.getConcertSeat().getConcertSeatId()
                                        );
                                    } catch (Exception e) {
                                        log.error("좌석 BOOKED 처리 실패: ticketId={}, error={}",
                                                ticket.getTicketId(), e.getMessage(), e);
                                        failedSeats.add(ticket.getConcertSeat().getConcertSeatId());
                                    }
                                });
                                if (!failedSeats.isEmpty()) {
                                    throw new BusinessException(ErrorCode.SEAT_BOOKING_FAILED,
                                            "일부 좌석 예약에 실패했습니다: " + failedSeats);
                                }
                            })
                            .subscribeOn(Schedulers.boundedElastic());
                })
                .then();
    }

    @Transactional
    public void handlePaymentFailure(String orderId, String errorCode, String errorMessage) {
        // 결제 실패 웹에서 호출시, 해당 결제/예매를 실패 및 취소 상태로 DB 처리함.
        paymentRepository.findByOrderId(orderId).ifPresent(payment -> {
            if (payment.getStatus() == PaymentStatus.PENDING) {
                payment.fail();
                payment.getBooking().cancel();
                log.info("결제 실패 상태로 변경 완료: orderId={}", orderId);
            }
        });
    }

    @Transactional
    public Mono<Void> cancelPayment(Booking booking,
                                    PaymentCancelRequest cancelRequest,
                                    Long currentUserId) {
        // 결제 취소 요청 처리: 본인 소유, DONE/부분취소 상태만 취소 가능
        return Mono.defer(() -> {
                    if (booking == null) {
                        return Mono.error(new BusinessException(ErrorCode.BOOKING_NOT_FOUND));
                    }
                    Payment payment = booking.getPayment();
                    if (payment == null) {
                        log.warn("예매(ID:{})에 결제 정보가 없어 취소를 스킵합니다.", booking.getBookingId());
                        return Mono.empty();
                    }
                    if (!payment.getUserId().equals(currentUserId)) {
                        log.warn("사용자 {}가 본인 결제(orderId:{})가 아닌 결제 취소를 시도했습니다.",
                                currentUserId, payment.getOrderId());
                        return Mono.error(new AccessDeniedException("본인의 결제만 취소할 수 있습니다."));
                    }
                    if (payment.getStatus() != PaymentStatus.DONE &&
                            payment.getStatus() != PaymentStatus.PARTIAL_CANCELED) {
                        log.info("취소할 수 없는 결제 상태({})입니다: orderId={}",
                                payment.getStatus(), payment.getOrderId());
                        return Mono.empty();
                    }
                    String raw = tossPaymentsProperties.secretKey() + ":";
                    String encodedKey = Base64.getEncoder()
                            .encodeToString(raw.getBytes(StandardCharsets.UTF_8));
                    return callTossCancelApi(payment.getPaymentKey(),
                            cancelRequest.getCancelReason(),
                            encodedKey)
                            .flatMap(tossResponse -> Mono.fromRunnable(() -> {
                                payment.cancel();
                                List<Map<String, Object>> cancels =
                                        (List<Map<String, Object>>) tossResponse.get("cancels");
                                if (cancels != null && !cancels.isEmpty()) {
                                    Map<String, Object> last = cancels.get(cancels.size() - 1);
                                    PaymentCancelHistory hist = PaymentCancelHistory.builder()
                                            .payment(payment)
                                            .transactionKey((String) last.get("transactionKey"))
                                            .cancelAmount(new BigDecimal(last.get("cancelAmount").toString()))
                                            .cancelReason((String) last.get("cancelReason"))
                                            .canceledAt(parseDateTime(last.get("canceledAt")))
                                            .build();
                                    paymentCancelHistoryRepository.save(hist);
                                }
                                log.info("결제 취소 완료 (async): orderId={}", payment.getOrderId());
                            }).subscribeOn(Schedulers.boundedElastic()))
                            .then();
                })
                .subscribeOn(Schedulers.boundedElastic());
    }

    @Transactional(readOnly = true)
    public List<PaymentHistoryDto> getPaymentHistoryByUserId(Long userId) {
        // 특정 사용자 ID로 결제 내역을 모두 조회하여 반환합니다.
        return paymentRepository.findByUserId(userId)
                .stream()
                .map(PaymentHistoryDto::new)
                .collect(Collectors.toList());
    }

    @Transactional
    public void updatePaymentStatusByWebhook(String orderId, String status) {
        // 웹훅 콜백을 통해 결제 상태 정보를 안전하게 DB에 반영합니다.(외부 API 기준으로 상태 동기화)
        log.info("웹훅을 통한 결제 상태 업데이트 시도: orderId={}, status={}", orderId, status);

        Payment payment = paymentRepository.findWithBookingByOrderId(orderId)
                .orElseThrow(() -> new BusinessException(ErrorCode.RESOURCE_NOT_FOUND,
                        "웹훅 처리: 결제 정보를 찾을 수 없습니다. orderId=" + orderId));
        PaymentStatus newStatus;
        try {
            newStatus = PaymentStatus.valueOf(status.toUpperCase());
        } catch (IllegalArgumentException e) {
            log.warn("웹훅 처리: 지원하지 않는 결제 상태값({})을 수신하여 처리를 건너뜁니다. orderId={}", status, orderId);
            return;
        }
        if (payment.getStatus().isFinalState() || payment.getStatus() == newStatus) {
            log.info("웹훅 처리: 이미 최종 상태이거나 상태 변경이 불필요하여 건너뜁니다. orderId={}, 현재상태={}, 요청상태={}",
                    orderId, payment.getStatus(), newStatus);
            return;
        }
        switch (newStatus) {
            case DONE:
                if (payment.getStatus() == PaymentStatus.PENDING) {
                    payment.complete(payment.getPaymentKey(), LocalDateTime.now());
                    payment.getBooking().confirm();
                    payment.getBooking().getTickets().forEach(ticket -> {
                        try {
                            seatStatusService.bookSeat(
                                    payment.getBooking().getConcert().getConcertId(),
                                    ticket.getConcertSeat().getConcertSeatId()
                            );
                            log.debug("웹훅: 좌석 상태 BOOKED로 변경 완료: concertId={}, seatId={}",
                                    payment.getBooking().getConcert().getConcertId(),
                                    ticket.getConcertSeat().getConcertSeatId());
                        } catch (Exception e) {
                            log.error("웹훅: 좌석 상태 BOOKED 변경 실패: concertId={}, seatId={}, error={}",
                                    payment.getBooking().getConcert().getConcertId(),
                                    ticket.getConcertSeat().getConcertSeatId(), e.getMessage());
                        }
                    });
                    log.info("웹훅: 결제 {} 상태 PENDING -> DONE 업데이트 완료", orderId);
                } else {
                    log.warn("웹훅: 잘못된 상태 전이 시도(DONE). orderId={}, 현재상태={}", orderId, payment.getStatus());
                }
                break;
            case CANCELED:
                if (payment.getStatus() == PaymentStatus.DONE || payment.getStatus() == PaymentStatus.PENDING) {
                    payment.cancel();
                    payment.getBooking().cancel();
                    log.info("웹훅: 결제 {} 상태 {} -> CANCELED 업데이트 완료", orderId, payment.getStatus());
                } else {
                    log.warn("웹훅: 잘못된 상태 전이 시도(CANCELED). orderId={}, 현재상태={}", orderId, payment.getStatus());
                }
                break;
            case FAILED:
            case EXPIRED:
                if (payment.getStatus() == PaymentStatus.PENDING) {
                    payment.fail();
                    payment.getBooking().cancel();
                    log.info("웹훅: 결제 {} 상태 PENDING -> {} 업데이트 완료", orderId, newStatus);
                } else {
                    log.warn("웹훅: 잘못된 상태 전이 시도({}). orderId={}, 현재상태={}", newStatus, orderId, payment.getStatus());
                }
                break;
            default:
                log.warn("웹훅 처리: 정의되지 않은 상태({})에 대한 로직이 없습니다. orderId={}", newStatus, orderId);
                break;
        }
    }

    private Mono<Map<String, Object>> callTossConfirmApi(PaymentConfirmRequest confirmRequest, String encodedSecretKey,
                                                         String idempotencyKey) {
        // 토스 결제 승인 API를 외부 호출하여 응답을 가져옵니다.
        return webClient.post()
                .uri("https://api.tosspayments.com/v1/payments/confirm")
                .header(HttpHeaders.AUTHORIZATION, "Basic " + encodedSecretKey)
                .header("Idempotency-Key", idempotencyKey)
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(Map.of(
                        "paymentKey", confirmRequest.getPaymentKey(),
                        "orderId", confirmRequest.getOrderId(),
                        "amount", confirmRequest.getAmount()
                ))
                .retrieve()
                .onStatus(HttpStatusCode::isError, response -> response.bodyToMono(String.class)
                        .flatMap(errorBody -> Mono.error(
                                new BusinessException(ErrorCode.TOSS_API_ERROR, "토스페이먼츠 승인 API 호출 실패: " + errorBody))))
                .bodyToMono(new ParameterizedTypeReference<>() {
                });
    }

    private Mono<Map<String, Object>> callTossCancelApi(String paymentKey, String cancelReason,
                                                        String encodedSecretKey) {
        // 토스 결제 취소 API를 외부 호출하여 응답을 가져옵니다.
        return webClient.post()
                .uri("https://api.tosspayments.com/v1/payments/{paymentKey}/cancel", paymentKey)
                .header(HttpHeaders.AUTHORIZATION, "Basic " + encodedSecretKey)
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(Map.of("cancelReason", cancelReason))
                .retrieve()
                .onStatus(HttpStatusCode::isError, response -> response.bodyToMono(String.class)
                        .flatMap(errorBody -> Mono.error(
                                new BusinessException(ErrorCode.TOSS_API_ERROR, "토스페이먼츠 취소 API 호출 실패: " + errorBody))))
                .bodyToMono(new ParameterizedTypeReference<>() {});
    }

    private LocalDateTime parseDateTime(Object dateTimeObj) {
        // 응답 데이터의 날짜/시간(문자열)을 LocalDateTime 객체로 변환합니다.
        String dateTimeStr = dateTimeObj.toString();
        try {
            return OffsetDateTime.parse(
                    dateTimeStr,
                    DateTimeFormatter.ISO_OFFSET_DATE_TIME
            ).toLocalDateTime();
        } catch (DateTimeParseException ex) {
            return LocalDateTime.parse(
                    dateTimeStr,
                    DateTimeFormatter.ISO_LOCAL_DATE_TIME
            );
        }
    }

    public String getBookingNumberByOrderId(String orderId) {
        // 주문ID에서 결제 엔티티를 조회하고, 해당 예매번호를 반환합니다.
        Payment payment = paymentRepository.findByOrderId(orderId)
                .orElseThrow(() -> new BusinessException(ErrorCode.RESOURCE_NOT_FOUND,
                        "존재하지 않는 주문 ID 입니다: " + orderId));
        if (payment.getBooking() == null) {
            throw new BusinessException(ErrorCode.RESOURCE_NOT_FOUND,
                    "결제에 연결된 예매 정보가 없습니다. orderId=" + orderId);
        }
        return payment.getBooking().getBookingNumber();
    }
}
