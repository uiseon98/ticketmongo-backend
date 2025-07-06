package com.team03.ticketmon.websocket.handler;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.team03.ticketmon.queue.domain.QueueStatus;
import com.team03.ticketmon.queue.dto.QueueStatusDto;
import com.team03.ticketmon.queue.service.WaitingQueueService;
import com.team03.ticketmon.websocket.MessageType;
import com.team03.ticketmon.websocket.WebSocketPayloadKeys;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.CloseStatus;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;
import org.springframework.web.socket.handler.TextWebSocketHandler;

import java.io.IOException;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 웹소켓 연결 및 메시지 처리를 담당하는 핸들러
 * 재연결 시 사용자의 상태를 확인하여 무한 대기를 방지하는 로직을 포함
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class CustomWebSocketHandler extends TextWebSocketHandler {

    private final Map<Long, WebSocketSession> sessions = new ConcurrentHashMap<>();
    private final ObjectMapper objectMapper;
    private final WaitingQueueService waitingQueueService; //  <-- 이 줄을 추가합니다.

    /**
     * 클라이언트와 WebSocket 연결이 성공적으로 맺어졌을 때 호출
     *
     * @param session 새로 연결된 클라이언트의 세션
     */
    @Override
    public void afterConnectionEstablished(WebSocketSession session) throws Exception {
        Long userId = extractUserId(session);
        Long concertId = extractConcertId(session);


        if (userId != null && concertId != null) {
            QueueStatusDto userStatus = waitingQueueService.getUserStatus(concertId, userId);

            if (userStatus.status() == QueueStatus.ADMITTED) {
                log.info("[WebSocket] 이미 입장한 사용자(ID: {})의 재연결 시도. 예매 페이지로 리디렉션 유도.", userId);
                Map<String, Object> redirectMessage = Map.of(
                        WebSocketPayloadKeys.TYPE, MessageType.REDIRECT_TO_RESERVE.name(),
                        WebSocketPayloadKeys.ACCESS_KEY, userStatus.accessKey()
                );
                session.sendMessage(new TextMessage(objectMapper.writeValueAsString(redirectMessage)));
                session.close(CloseStatus.NORMAL.withReason("Already admitted"));
                return;
            }

            addSession(userId, session);
            log.debug("WebSocket 연결됨. 사용자: {}, 세션 ID: {}", userId, session.getId());
        } else {
            log.warn("사용자 ID 또는 콘서트 ID 없이 WebSocket 연결 시도됨. 세션 ID: {}, URI: {}", session.getId(), session.getUri());
            session.close(CloseStatus.POLICY_VIOLATION.withReason("User or Concert ID not found"));
        }
    }

    /**
     * 텍스트 메시지를 수신했을 때 호출됩니다.
     * 현재 시스템에서는 클라이언트로부터 메시지를 받는 로직은 없지만, 향후 확장을 위해 남겨둡니다.
     * (예: 클라이언트가 "ping"을 보내 연결을 유지하는 로직)
     * * @param session 메시지를 보낸 클라이언트의 세션
     * @param message 수신된 텍스트 메시지
     */
    @Override
    protected void handleTextMessage(WebSocketSession session, TextMessage message) throws Exception {
        Long userId = extractUserId(session);
        log.debug("메시지 수신. 사용자: {}, 내용: {}", userId, message.getPayload());
        // 여기에 클라이언트가 보낸 메시지를 처리하는 로직 추가 가능
    }

    /**
     * 클라이언트와 WebSocket 연결이 끊어졌을 때 호출됩니다.
     *
     * @param session 연결이 끊어진 클라이언트의 세션
     * @param status  연결 종료 상태 (코드 및 원인)
     */
    @Override
    public void afterConnectionClosed(WebSocketSession session, CloseStatus status) throws Exception {
        Long userId = extractUserId(session);
        if (userId != null) {
            sessions.remove(userId);
            log.debug("WebSocket 연결 종료됨. 사용자: {}, 세션 ID: {}, 상태: {}", userId, session.getId(), status);
        }
    }

    /**
     * 특정 사용자에게 메시지(Payload)를 전송
     *
     * @param userId  메시지를 받을 사용자 ID
     * @param payload 전송할 데이터 (Map 형태, JSON으로 변환됨)
     */
    public void sendMessageToUser(Long userId, Map<String, Object> payload) {
        WebSocketSession session = sessions.get(userId);
        if (session != null && session.isOpen()) {
            try {
                String message = objectMapper.writeValueAsString(payload);
                session.sendMessage(new TextMessage(message));
                log.debug("메시지 전송 성공. 사용자: {}, 내용: {}", userId, message);
            } catch (IOException e) {
                log.error("WebSocket 메시지 전송 실패! 사용자: {}", userId, e);
            }
        } else {
            // WARN 레벨: 알림을 보내야 할 사용자의 세션이 없는 경우, 중요한 이벤트 누락 가능성
            log.warn("메시지를 전송할 수 없음. 세션이 존재하지 않거나 닫혀있음. 사용자: {}", userId);
        }
    }


    /**
     * 사용자 ID를 추출
     *
     * @param session 클라이언트 세션
     * @return 추출된 사용자 ID, 없으면 null
     */
    private Long extractUserId(WebSocketSession session) {
        // WebSocket Handshake 과정에서 인터셉터를 통해 JWT 같은 인증 토큰을 검증하고 사용자 ID를 추출해야 합니다.
        Map<String, Object> attributes = session.getAttributes();
        return (Long) attributes.get("userId");
    }

    /**
     * 콘서트 ID를 추출
     *
     * @param session 클라이언트 세션
     * @return 추출된 콘서트 ID, 없으면 null
     */
    private Long extractConcertId(WebSocketSession session) {
        Map<String, Object> attributes = session.getAttributes();
        return (Long) attributes.get("concertId");
    }


    // 테스트와 외부에서 세션을 추가하기 위한 public 메서드
    public void addSession(Long userId, WebSocketSession newSession) {
        WebSocketSession oldSession = sessions.put(userId, newSession);
        if (oldSession != null && oldSession.isOpen()) {
            try {
                oldSession.close(CloseStatus.NORMAL);
                log.debug("기존 WebSocket 세션 강제 종료: 사용자={}, 세션ID={}", userId, oldSession.getId());
            } catch (IOException e) {
                log.warn("기존 WebSocket 세션 종료 실패: 사용자={}, 세션ID={}", userId, oldSession.getId(), e);
            }
        }
        log.debug("새 WebSocket 세션 등록: 사용자={}, 세션ID={}", userId, newSession.getId());
    }
}