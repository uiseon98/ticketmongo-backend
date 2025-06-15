package com.team03.ticketmon._global.exception;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.Getter;

/**
 * ✅ SuccessResponse: 공통 성공 응답 클래스<br>
 * -----------------------------------------------------<br>
 * 모든 API 성공 응답은 이 클래스를 통해 통일된 형태로 반환됩니다.<br><br>
 *
 * 📌 기본 구조:
 * <ul>
 *     <li>success : 항상 true</li>
 *     <li>message : 클라이언트에게 보여줄 성공 메시지 (nullable)</li>
 *     <li>data : 반환할 실제 데이터</li>
 * </ul>
 * <br>
 * 📌 사용 예시:
 * <ul>
 *     <li>단순 응답: <code>SuccessResponse.of(data)</code></li>
 *     <li>메시지 포함 응답: <code>SuccessResponse.of("조회 성공", data)</code></li>
 * </ul>
 */
@Getter
@JsonInclude(JsonInclude.Include.NON_NULL) // null인 필드는 JSON 응답에서 제외
public class SuccessResponse<T> {

    private final boolean success = true;   // 항상 true로 고정
    private String message;                 // 선택적 메시지 (성공 안내 등)
    private T data;                         // 응답 데이터

    /**
     * 데이터만 포함하는 성공 응답 생성자
     *
     * @param data 반환할 데이터
     */
    private SuccessResponse(T data) {
        this.data = data;
    }

    /**
     * 메시지와 데이터를 모두 포함하는 성공 응답 생성자
     *
     * @param message 클라이언트에 전달할 메시지
     * @param data    반환할 데이터
     */
    private SuccessResponse(String message, T data) {
        this.message = message;
        this.data = data;
    }

    /**
     * ✅ 정적 팩토리 메서드: 데이터만 포함
     *
     * @param data 반환할 데이터
     * @return 성공 응답 객체
     */
    public static <T> SuccessResponse<T> of(T data) {
        return new SuccessResponse<>(data);
    }

    /**
     * ✅ 정적 팩토리 메서드: 메시지 + 데이터 포함
     *
     * @param message 클라이언트에 전달할 메시지
     * @param data    반환할 데이터
     * @return 성공 응답 객체
     */
    public static <T> SuccessResponse<T> of(String message, T data) {
        return new SuccessResponse<>(message, data);
    }
}