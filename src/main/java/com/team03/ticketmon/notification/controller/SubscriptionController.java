package com.team03.ticketmon.notification.controller;

import com.team03.ticketmon.notification.domain.enums.SubscriptionType;
import com.team03.ticketmon.notification.dto.SubscriptionRequest;
import com.team03.ticketmon.notification.dto.UnsubscribeRequest;
import com.team03.ticketmon.notification.repository.SubscriptionRepository;
import com.team03.ticketmon.notification.service.SubscriptionService;
import com.team03.ticketmon.user.domain.entity.UserEntity;
import com.team03.ticketmon.user.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.security.Principal;
import java.util.Map;

@Slf4j
@RestController
@RequestMapping("/api/notifications")
@RequiredArgsConstructor
public class SubscriptionController {

    private final SubscriptionService subscriptionService;
    private final UserRepository userRepository;
    private final SubscriptionRepository subscriptionRepo;

    /**
     * 로그인한 사용자의 PUSH 구독 여부를 반환
     */
    @GetMapping("/status")
    public ResponseEntity<Map<String, Boolean>> status(Principal principal) {
        // 인증 정보(Principal)가 없으면 401
        if (principal == null) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
        }

        // username → UserEntity → userId
        String username = principal.getName();
        UserEntity user = userRepository.findByUsername(username)
                .orElseThrow(() -> new IllegalArgumentException("존재하지 않는 사용자: " + username));
        Long userId = user.getId();

        // 실제로 Subscription이 SUBSCRIBED 상태로 존재하는지 검사
        boolean subscribed = subscriptionService.isSubscribed(userId, SubscriptionType.PUSH);

        return ResponseEntity.ok(Map.of("subscribed", subscribed));
    }

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
        subscriptionService.subscribe(userId, req.getPlayerId());
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
        subscriptionService.unsubscribe(req.getPlayerId());
        return ResponseEntity.ok().build();
    }
}
