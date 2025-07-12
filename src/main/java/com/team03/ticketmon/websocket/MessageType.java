package com.team03.ticketmon.websocket;

public enum MessageType {
    // 서버 -> 클라이언트
    ADMIT,                  // 입장 허가
    RANK_UPDATE,            // 실시간 순위 업데이트
    REDIRECT_TO_RESERVE,    // 예매 페이지로 리디렉션 (재연결 시)
    ERROR;                  // 에러 알림
}