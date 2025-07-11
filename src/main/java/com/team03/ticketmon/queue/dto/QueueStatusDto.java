package com.team03.ticketmon.queue.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.team03.ticketmon.queue.domain.QueueStatus;

/**
 * ✅ QueueStatusDto: 대기열 관련 통합 응답 DTO<br>
 * -----------------------------------------------------<br>
 * 대기열 관련 요청에 대한 상태, 순위, 접근 키, 메시지를 포함한 응답을 제공<br>
 * 정적 팩토리 메서드를 통해 객체 생성을 단순화하고 일관성을 유지
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record QueueStatusDto(
        QueueStatus status,
        Long rank,
        String accessKey,
        String message
) {

    /**
     * ✅ 정적 팩토리 메서드: 대기열 등록 응답 생성 (WAITING)
     *
     * @param rank 대기열 내 순위
     * @return EnterResponse 객체
     */
    public static QueueStatusDto waiting(Long rank) {
        return new QueueStatusDto(QueueStatus.WAITING, rank, null, "현재 대기 중입니다.");
    }

    /**
     * ✅ 정적 팩토리 메서드: 즉시 입장 응답 생성 (IMMEDIATE_ENTRY)
     *
     * @param accessKey 사용자에게 부여된 접근 키
     * @return EnterResponse 객체
     */
    public static QueueStatusDto immediateEntry(String accessKey) {
        return new QueueStatusDto(QueueStatus.IMMEDIATE_ENTRY, null, accessKey, "즉시 입장이 가능합니다.");
    }

    /**
     * [신규] ✅ 정적 팩토리 메서드: 입장 허가 응답 생성 (ADMITTED)
     * 이미 입장 허가를 받은 사용자의 상태를 조회했을 때 사용됩니다.
     * @param accessKey 사용자에게 부여된 접근 키
     * @return QueueStatusDto 객체
     */
    public static QueueStatusDto admitted(String accessKey) {
        return new QueueStatusDto(QueueStatus.ADMITTED, null, accessKey, "입장이 허가된 상태입니다.");
    }

    /**
     * [신규] ✅ 정적 팩토리 메서드: 대기열 이탈/만료 응답 생성 (EXPIRED_OR_NOT_IN_QUEUE)
     * 대기열에 정보가 없거나 만료된 사용자의 상태를 조회했을 때 사용됩니다.
     * @return QueueStatusDto 객체
     */
    public static QueueStatusDto expiredOrNotInQueue() {
        return new QueueStatusDto(QueueStatus.EXPIRED_OR_NOT_IN_QUEUE, null, null, "대기열에 정보가 없거나 만료되었습니다.");
    }

    /**
     * ✅ 정적 팩토리 메서드: 에러 응답 생성 (ERROR)
     *
     * @param message 사용자에게 보여줄 에러 메시지
     * @return EnterResponse 객체
     */
    public static QueueStatusDto error(String message) {
        return new QueueStatusDto(QueueStatus.ERROR, null, null, message);
    }
}