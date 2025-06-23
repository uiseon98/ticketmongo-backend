package com.team03.ticketmon.payment.controller;

import java.io.IOException;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.team03.ticketmon.payment.service.PaymentService;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Slf4j // 로그 출력을 위한 Lombok 어노테이션
@RestController // 모든 메서드가 JSON 응답
@RequestMapping("/api/v1/webhooks/toss") // 토스페이먼츠 웹훅 전용 URL
@RequiredArgsConstructor // 생성자 주입 자동 생성
public class WebhookController {

	private final ObjectMapper objectMapper = new ObjectMapper(); // JSON 파싱용
	private final PaymentService paymentService; // 결제 상태 갱신 등 비즈니스 로직

	/**
	 * 토스페이먼츠 웹훅 수신 API
	 * - 토스에서 결제 상태 변경(가상계좌 입금, 취소 등) 이벤트 발생 시 호출
	 * - 웹훅 페이로드(JSON)를 파싱하여 결제 상태를 갱신
	 */
	@PostMapping("/payment-updates")
	public ResponseEntity<String> handleTossPaymentWebhook(@RequestBody String payload) {
		log.info("토스페이먼츠 웹훅 수신: {}", payload);
		try {
			// JSON 파싱
			JsonNode jsonNode = objectMapper.readTree(payload);
			String eventType = jsonNode.get("eventType").asText();

			// 결제 상태 변경 이벤트만 처리
			if ("PAYMENT_STATUS_CHANGED".equals(eventType)) {
				JsonNode data = jsonNode.get("data");
				String orderId = data.get("orderId").asText(); // 주문 ID
				String status = data.get("status").asText();   // 상태(DONE, CANCELED 등)
				// 결제 상태 갱신
				paymentService.updatePaymentStatusByWebhook(orderId, status);
			}
			// (보안) 실제 운영 환경에서는 웹훅 서명 검증 필요

			// 성공 응답(토스 서버에 2xx 응답을 보내야 재전송이 발생하지 않음)
			return ResponseEntity.ok("Webhook processed successfully.");
		} catch (IOException e) {
			log.error("웹훅 페이로드 파싱 실패: {}", e.getMessage(), e);
			// 파싱 실패해도 토스 서버에는 2xx 응답을 보내야 재전송이 막힘
			return ResponseEntity.ok("Webhook payload parsing error, but acknowledged.");
		}
	}
}
