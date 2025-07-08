package com.team03.ticketmon.notification.controller;

import com.team03.ticketmon.notification.dto.SubscriptionRequest;
import com.team03.ticketmon.notification.dto.UnsubscribeRequest;
import com.team03.ticketmon.notification.service.SubscriptionService;
import com.team03.ticketmon.user.domain.entity.UserEntity;
import com.team03.ticketmon.user.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.security.Principal;

@Slf4j
@RestController
@RequestMapping("/api/notifications")
@RequiredArgsConstructor
public class SubscriptionController {

    private final SubscriptionService service;
    private final UserRepository userRepository;

    @PostMapping("/subscribe")
    public ResponseEntity<Void> subscribe(
            @RequestBody SubscriptionRequest req,
            Principal principal
    ) {
        if (principal == null) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
        }
        // 1) username 조회
        String username = principal.getName();
        // 2) User 엔티티에서 ID 얻기
        UserEntity user = userRepository.findByUsername(username)
                .orElseThrow(() -> new IllegalArgumentException("존재하지 않는 사용자: " + username));
        Long userId = user.getId();
        // 3) 구독 처리
        service.subscribe(userId, req.getPlayerId());
        return ResponseEntity.ok().build();
    }


    @PostMapping("/unsubscribe")
    public ResponseEntity<Void> unsubscribe(
            @RequestBody UnsubscribeRequest req,
            Principal principal
    ) {
        if (principal == null) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
        }
        // (userId가 필요 없으면 이 부분 생략 가능)
        String username = principal.getName();
        userRepository.findByUsername(username)
                .orElseThrow(() -> new IllegalArgumentException("존재하지 않는 사용자: " + username));
        service.unsubscribe(req.getPlayerId());
        return ResponseEntity.ok().build();
    }
}
