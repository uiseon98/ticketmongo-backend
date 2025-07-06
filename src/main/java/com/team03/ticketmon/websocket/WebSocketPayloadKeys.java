package com.team03.ticketmon.websocket;

/**
 * WebSocket 메시지 페이로드에 사용되는 키(key)들을 상수로 관리하는 클래스입니다.
 * 매직 스트링을 방지하고 코드의 일관성과 안정성을 높입니다.
 */
public final class WebSocketPayloadKeys {

    // 인스턴스화 방지
    private WebSocketPayloadKeys() {}

    public static final String TYPE = "type";
    public static final String RANK = "rank";
    public static final String ACCESS_KEY = "accessKey";
}