package com.team03.ticketmon.payment.controller;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.security.InvalidKeyException;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Base64;
import java.util.stream.Collectors;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.team03.ticketmon.payment.config.TossPaymentsProperties;
import com.team03.ticketmon.payment.service.PaymentService;

import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@RestController
@RequestMapping("/api/v1/webhooks/toss")
@RequiredArgsConstructor
public class WebhookController {

	private final ObjectMapper objectMapper = new ObjectMapper();
	private final PaymentService paymentService;
	private final TossPaymentsProperties tossPaymentsProperties;

	@PostMapping("/payment-updates")
	public ResponseEntity<String> handleTossPaymentWebhook(HttpServletRequest request) {
		// 토스페이먼츠가 외부에서 보내오는 "웹훅" 이벤트를 처리하는 엔드포인트입니다.
		// 실제 결제 승인/취소 등 변동사항이 있을 때 서버에서 백엔드 상태를 맞추는 용도입니다.
		try {
			// 1. HTTP 요청 바디 전체를 원본 그대로 String으로 읽어옵니다
			String requestBody = request.getReader().lines().collect(Collectors.joining(System.lineSeparator()));
			JsonNode jsonNode = objectMapper.readTree(requestBody);
			String eventType = jsonNode.get("eventType").asText();

			// 2. "PAYMENT_STATUS_CHANGED" 이벤트가 아니면 서명(HMAC) 검증을 반드시 수행합니다
			if (!"PAYMENT_STATUS_CHANGED".equals(eventType)) {
				verifySignature(request, requestBody);
				log.info("토스페이먼츠 웹훅 서명 검증 성공 (eventType: {})", eventType);
			} else {
				log.info("PAYMENT_STATUS_CHANGED 이벤트이므로 서명 검증을 건너뜁니다.");
			}

			// 3. 실제 결제 상태 등 비즈니스 동작 처리
			if ("PAYMENT_STATUS_CHANGED".equals(eventType)) {
				JsonNode data = jsonNode.get("data");
				String orderId = data.get("orderId").asText();
				String status = data.get("status").asText();
				paymentService.updatePaymentStatusByWebhook(orderId, status);
			}

			// 4. 정상 응답(200 OK) 반환: 토스 서버가 재전송 안하도록
			return ResponseEntity.ok("Webhook processed successfully.");
		} catch (SecurityException e) {
			// HMAC 등 서명 검증 실패 시, 403 에러로 거절
			log.warn("웹훅 서명 검증 실패: {}", e.getMessage());
			return ResponseEntity.status(HttpStatus.FORBIDDEN).body(e.getMessage());
		} catch (IOException e) {
			// JSON 파싱 실패 등 문제가 있어도, 200 OK로 응답(재전송 방지)
			log.error("웹훅 페이로드 파싱 실패: {}", e.getMessage(), e);
			return ResponseEntity.ok("Webhook payload parsing error, but acknowledged.");
		} catch (Exception e) {
			// 그 외 예기치 않은 에러 처리
			log.error("웹훅 처리 중 알 수 없는 오류 발생: {}", e.getMessage(), e);
			return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body("Internal server error.");
		}
	}

	private void verifySignature(HttpServletRequest request, String payload) throws SecurityException {
		// 웹훅 서명(HMAC-SHA256) 검증 메서드
		// 요청 헤더와 body, 비밀키를 사용해서 위조/변조 없이 온 것인지 확인합니다
		String signature = request.getHeader("tosspayments-webhook-signature");
		String transmissionTime = request.getHeader("tosspayments-webhook-transmission-time");

		// 둘 중 하나라도 없으면 예외
		if (signature == null || transmissionTime == null) {
			throw new SecurityException("웹훅 서명 또는 시간 헤더가 누락되었습니다.");
		}
		String dataToSign = payload + ":" + transmissionTime;
		String secretKey = tossPaymentsProperties.secretKey();

		try {
			Mac sha256_HMAC = Mac.getInstance("HmacSHA256");
			SecretKeySpec secret_key_spec = new SecretKeySpec(secretKey.getBytes(StandardCharsets.UTF_8), "HmacSHA256");
			sha256_HMAC.init(secret_key_spec);

			byte[] hash = sha256_HMAC.doFinal(dataToSign.getBytes(StandardCharsets.UTF_8));
			String calculatedSignature = Base64.getEncoder().encodeToString(hash);

			// 계산 결과와 실제 헤더 값을 안전하게 비교
			if (!MessageDigest.isEqual(calculatedSignature.getBytes(StandardCharsets.UTF_8),
					signature.getBytes(StandardCharsets.UTF_8))) {
				throw new SecurityException("계산된 서명이 헤더의 서명과 일치하지 않습니다.");
			}
		} catch (NoSuchAlgorithmException | InvalidKeyException e) {
			log.error("웹훅 서명 검증 중 암호화 오류 발생", e);
			throw new SecurityException("서명 검증 과정에서 내부 오류가 발생했습니다.", e);
		}
	}
}
