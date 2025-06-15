package com.team03.ticketmon._global.exception;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

/**
 * ✅ ErrorCode 정의
 * <p>
 * 이 Enum은 모든 비즈니스 및 시스템 예외 코드를 통합 관리합니다.
 * <br><br>
 * 각 항목은 다음 정보를 포함합니다:
 * <ul>
 *   <li>HTTP 상태 코드 (status)</li>
 *   <li>내부 에러 코드 (code) - 팀 표준 형식 (예: "C001")</li>
 *   <li>사용자에게 전달할 메시지 (message)</li>
 * </ul>
 * <br>
 * 예외 발생 시 {@link BusinessException}과 함께 사용되며,
 * {@link ErrorResponse}를 통해 클라이언트에게 표준화된 응답을 제공합니다.
 */
@Getter
@RequiredArgsConstructor
public enum ErrorCode {

    // Common (공통 예외)
    INVALID_INPUT(400, "C001", "유효하지 않은 입력값입니다."),
    RESOURCE_NOT_FOUND(404, "C002", "리소스를 찾을 수 없습니다."),
    SERVER_ERROR(500, "C003", "서버에 오류가 발생했습니다."),

    // Auth & User (인증 및 사용자 관련)
    LOGIN_FAILED(401, "A001", "아이디 또는 비밀번호가 일치하지 않습니다."),
    INVALID_TOKEN(401, "A002", "인증 토큰이 유효하지 않습니다."),
    ACCESS_DENIED(403, "A003", "해당 리소스에 접근할 권한이 없습니다."),
    EMAIL_DUPLICATION(409, "A004", "이미 가입된 이메일입니다."),
    SELLER_APPLY_ONCE(400, "A005", "판매자 권한 신청은 한 번만 가능합니다."),

    // Permission & Admin (권한 및 관리자)
    ADMIN_ACCESS_DENIED(403, "P001", "관리자만 접근할 수 있습니다."),

    // Ticket & Concert (티켓 및 콘서트)
    SEARCH_BAD_REQUEST(400, "T001", "콘서트 검색어는 2글자 이상 입력해주세요."),
    INVALID_DATE_RANGE(400, "T002", "조회 가능한 날짜 범위를 벗어났습니다."),

    // Booking & Seat (예매 및 좌석)
    BOOKING_NOT_AVAILABLE(403, "B001", "예매가 가능한 상태가 아닙니다."),
    QUEUE_REQUIRED(429, "B002", "현재 접속자가 많아 대기열에 등록되었습니다."),
    SEAT_ALREADY_TAKEN(409, "B003", "이미 선택된 좌석입니다."),
    LOCK_ACQUISITION_FAILED(503, "B004", "좌석 선점에 실패했습니다. (락 획득 실패)"),
    BOOKING_TIMEOUT(408, "B005", "좌석 선점 시간이 만료되었습니다."),

    // Payment (결제)
    PAYMENT_AMOUNT_MISMATCH(400, "M001", "주문 금액이 일치하지 않습니다."),
    PAYMENT_FAILED(402, "M002", "결제에 실패했습니다.");

    /**
     * HTTP 상태 코드 (예: 400, 404, 500 등)
     */
    private final int status;

    /**
     * 내부 비즈니스 에러 코드 (예: "C001", "A003")<br>
     * - 코드 체계는 접두어로 도메인 분류 가능
     */
    private final String code;

    /**
     * 사용자에게 전달할 에러 메시지
     */
    private final String message;
}