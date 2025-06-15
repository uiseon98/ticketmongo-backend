package com.team03.ticketmon._global.exception;

import lombok.Getter;
import org.springframework.http.HttpStatus;

/**
 * ✅ ErrorResponse: 에러 응답 통일 클래스<br>
 * ------------------------------------------------<br>
 * 이 클래스는 모든 API 예외 응답의 형식을 통일하기 위해 사용됩니다.<br><br>
 *
 * 📌 기본 구조:<br>
 * - success : 항상 false (성공 응답은 SuccessResponse 사용)<br>
 * - status  : HTTP 상태 코드 (예: 400, 401, 500 등)<br>
 * - code    : 내부 비즈니스 에러 코드 (예: "A001", "B003")<br>
 * - message : 사용자에게 보여줄 메시지<br><br>
 *
 * ✅ 사용 예시:<br>
 * - ErrorCode 기반: ErrorResponse.of(ErrorCode.LOGIN_FAILED)<br>
 * - 커스텀 메시지: ErrorResponse.of(HttpStatus.INTERNAL_SERVER_ERROR, "서버에 문제가 발생했습니다.")
 */
@Getter
public class ErrorResponse {

    private final boolean success = false;
    private final int status;
    private final String code;
    private final String message;

    /**
     * ErrorCode 기반 생성자<br>
     * - 대부분의 예외는 사전에 정의된 ErrorCode enum을 기반으로 생성
     *
     * @param errorCode ErrorCode enum 값
     */
    private ErrorResponse(ErrorCode errorCode) {
        this.status = errorCode.getStatus();
        this.code = errorCode.getCode();
        this.message = errorCode.getMessage();
    }

    /**
     * 정적 팩토리 메서드 (ErrorCode 기반) <br>
     * - 비즈니스 예외 응답용으로 주로 사용
     *
     * @param errorCode 사전 정의된 ErrorCode
     * @return ErrorResponse 인스턴스
     */
    public static ErrorResponse of(ErrorCode errorCode) {
        return new ErrorResponse(errorCode);
    }

    /**
     * 정적 팩토리 메서드 (오버로딩 / 커스텀 메시지 기반)<br>
     * - 일반적인 Exception 대응용
     *
     * @param httpStatus HTTP 상태
     * @param message    사용자에게 전달할 메시지
     * @return ErrorResponse 인스턴스
     */
    public static ErrorResponse of(HttpStatus httpStatus, String message) {
        return new ErrorResponse(httpStatus.value(), httpStatus.name(), message);
    }

    /**
     * HttpStatus와 직접 입력한 메시지를 기반으로 생성<br>
     * - 예상하지 못한 일반 예외 처리에 사용(커스텀)
     *
     * @param status  HttpStatus 값 (예: INTERNAL_SERVER_ERROR)
     * @param code    에러 코드 문자열 (보통 status.name() 사용)
     * @param message 클라이언트에게 보여줄 메시지
     */
    private ErrorResponse(int status, String code, String message) {
        this.status = status;
        this.code = code;
        this.message = message;
    }
}