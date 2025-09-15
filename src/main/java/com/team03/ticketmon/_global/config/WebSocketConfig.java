package com.team03.ticketmon._global.config;

import com.team03.ticketmon.auth.jwt.WebSocketAuthInterceptor;
import com.team03.ticketmon.websocket.handler.CustomWebSocketHandler;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.socket.config.annotation.EnableWebSocket;
import org.springframework.web.socket.config.annotation.WebSocketConfigurer;
import org.springframework.web.socket.config.annotation.WebSocketHandlerRegistry;

import java.util.Collections;
import java.util.List;
import java.util.Optional;

/**
 * WebSocket 관련 설정을 구성하는 클래스
 */
@Configuration
@EnableWebSocket // WebSocket 활성화
@RequiredArgsConstructor
public class WebSocketConfig implements WebSocketConfigurer {

    private final CustomWebSocketHandler customWebSocketHandler;
    private final WebSocketAuthInterceptor webSocketAuthInterceptor;
    private final CorsProperties corsProperties;

    /**
     * WebSocket 핸들러를 특정 경로에 등록
     *
     * @param registry 핸들러를 등록할 레지스트리
     */
    @Override
    public void registerWebSocketHandlers(WebSocketHandlerRegistry registry) {

        // corsProperties에서 허용된 Origin 목록을 가져옵니다.
        // 이 변수는 반드시 registry를 사용하기 전에 메서드 내부에 선언되어야 합니다.
        List<String> allowedOrigins = Optional.ofNullable(corsProperties.getAllowedOrigins())
                .orElse(Collections.emptyList());

        registry
                // 클라이언트가 "/ws/waitqueue" 경로로 WebSocket 연결을 맺을 수 있도록 핸들러를 매핑
                .addHandler(customWebSocketHandler, "/ws/waitqueue")
                // 주입받은 인터셉터를 등록
                .addInterceptors(webSocketAuthInterceptor)
                // List<String>을 String[] 배열로 변환하여 설정합니다.
                .setAllowedOrigins(allowedOrigins.toArray(new String[0]));
    }
}