package com.team03.ticketmon.websocket;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.CloseStatus;
import org.springframework.web.socket.WebSocketSession;

import java.io.IOException;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

/**
 * 활성 WebSocket 세션을 중앙에서 관리하는 매니저 클래스
 * 세션의 등록, 제거, 조회 기능을 담당합니다.
 */
@Slf4j
@Component
public class WebSocketSessionManager {

    private final Map<Long, WebSocketSession> sessions = new ConcurrentHashMap<>();

    /**
     * 새로운 세션을 등록하고, 동일한 사용자의 기존 세션이 있다면 종료시킵니다.
     */
    public void addSession(Long userId, WebSocketSession newSession) {
        WebSocketSession oldSession = sessions.put(userId, newSession);
        if (oldSession != null && oldSession.isOpen()) {
            try {
                oldSession.close(CloseStatus.NORMAL.withReason("New session connected"));
                log.debug("기존 WebSocket 세션 강제 종료: 사용자={}, 세션ID={}", userId, oldSession.getId());
            } catch (IOException e) {
                log.warn("기존 WebSocket 세션 종료 실패: 사용자={}, 세션ID={}", userId, oldSession.getId(), e);
            }
        }
        log.debug("새 WebSocket 세션 등록: 사용자={}, 세션ID={}", userId, newSession.getId());
    }

    /**
     * 세션을 제거합니다.
     */
    public void removeSession(Long userId) {
        sessions.remove(userId);
    }

    /**
     * 특정 사용자의 세션을 조회합니다.
     */
    public WebSocketSession getSession(Long userId) {
        return sessions.get(userId);
    }

    /**
     * 현재 연결된 모든 세션의 개수를 반환합니다.
     */
    public int getSessionCount() {
        return sessions.size();
    }

    /**
     * 현재 연결된 모든 사용자의 ID 목록을 반환합니다.
     */
    public Map<Long, String> getConnectedUsers() {
        return sessions.entrySet().stream()
                .collect(Collectors.toMap(
                        Map.Entry::getKey,
                        entry -> entry.getValue().getId()
                ));
    }
}