package com.team03.ticketmon._global.exception;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

/**
 * ✅ GlobalExceptionHandler
 * <p>
 * 애플리케이션 전역에서 발생하는 예외를 한 곳에서 처리하는 클래스입니다.
 * <br><br>
 * 주요 처리 방식:
 * <ul>
 *   <li>{@link BusinessException} : 비즈니스 로직 중 발생하는 커스텀 예외</li>
 *   <li>{@link Exception} : 예상치 못한 모든 예외 (Fallback)</li>
 * </ul>
 * <br>
 * 반환 형식은 모두 {@link ErrorResponse}를 사용하여 클라이언트에 통일된 구조로 전달됩니다.
 */
@RestControllerAdvice
public class GlobalExceptionHandler {

    /**
     * ✅ 우리가 직접 정의한 비즈니스 예외 처리
     * <p>
     * ErrorCode 기반으로 구성된 {@link BusinessException}을 받아서<br>
     * HTTP 상태 코드와 메시지를 {@link ErrorResponse} 형태로 반환합니다.
     *
     * @param e BusinessException (비즈니스 로직 중 의도적으로 발생시킨 예외)
     * @return 통일된 에러 응답 (ResponseEntity<ErrorResponse>)
     */
    @ExceptionHandler(BusinessException.class)
    protected ResponseEntity<ErrorResponse> handleBusinessException(BusinessException e) {
        ErrorCode errorCode = e.getErrorCode();
        ErrorResponse response = ErrorResponse.of(errorCode);
        return new ResponseEntity<>(response, HttpStatus.valueOf(errorCode.getStatus()));
    }

    /**
     * ✅ 시스템 예외 등 모든 기타 예외 처리 (최후의 보루)
     * <p>
     * 개발자가 명시적으로 처리하지 않은 모든 예외는 이 블록에서 처리됩니다.<br>
     * 서버 내부 오류(500)로 간주하고 에러 메시지를 포함한 {@link ErrorResponse}를 반환합니다.
     *
     * @param e 예상하지 못한 예외 (NullPointerException 등)
     * @return 500 에러 응답 (ResponseEntity<ErrorResponse>)
     */
    @ExceptionHandler(Exception.class)
    protected ResponseEntity<ErrorResponse> handleException(Exception e) {
        // TODO: 로그 기록 필요 시 아래에서 log.error 등 활용 가능
        ErrorResponse response = ErrorResponse.of(HttpStatus.INTERNAL_SERVER_ERROR, "서버에 오류가 발생했습니다.");
        return new ResponseEntity<>(response, HttpStatus.INTERNAL_SERVER_ERROR);
    }
}