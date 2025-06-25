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
	private final TossPaymentsProperties tossPaymentsProperties; // 💡 [추가] 시크릿 키를 담고 있는 프로퍼티 클래스

	/**
	 * 토스페이먼츠 웹훅 수신 API
	 * - 서명 검증을 통해 위조된 요청을 차단
	 */
	@PostMapping("/payment-updates")
	public ResponseEntity<String> handleTossPaymentWebhook(
		HttpServletRequest request) { // 💡 [수정] HttpServletRequest를 직접 받음
		try {
			// 1. 서명 검증
			String requestBody = request.getReader().lines().collect(Collectors.joining(System.lineSeparator()));
			verifySignature(request, requestBody); // 검증 실패 시 예외 발생
			log.info("토스페이먼츠 웹훅 서명 검증 성공");

			// 2. 서명 검증 성공 후, 비즈니스 로직 처리
			JsonNode jsonNode = objectMapper.readTree(requestBody);
			String eventType = jsonNode.get("eventType").asText();

			if ("PAYMENT_STATUS_CHANGED".equals(eventType)) {
				JsonNode data = jsonNode.get("data");
				String orderId = data.get("orderId").asText();
				String status = data.get("status").asText();
				paymentService.updatePaymentStatusByWebhook(orderId, status);
			}

			return ResponseEntity.ok("Webhook processed successfully.");
		} catch (SecurityException e) {
			log.warn("웹훅 서명 검증 실패: {}", e.getMessage());
			return ResponseEntity.status(HttpStatus.FORBIDDEN).body(e.getMessage());
		} catch (IOException e) {
			log.error("웹훅 페이로드 파싱 실패: {}", e.getMessage(), e);
			return ResponseEntity.ok("Webhook payload parsing error, but acknowledged.");
		} catch (Exception e) {
			log.error("웹훅 처리 중 알 수 없는 오류 발생: {}", e.getMessage(), e);
			return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body("Internal server error.");
		}
	}

	/**
	 * 토스페이먼츠 웹훅 서명을 검증하는 private 헬퍼 메서드
	 * @param request HttpServletRequest 객체
	 * @param payload 웹훅 요청의 원본 바디 (JSON 문자열)
	 * @throws SecurityException 서명 검증 실패 시
	 */
	private void verifySignature(HttpServletRequest request, String payload) throws SecurityException {
		String signature = request.getHeader("tosspayments-webhook-signature");
		String transmissionTime = request.getHeader("tosspayments-webhook-transmission-time");

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

			// 타이밍 공격에 안전한 비교를 위해 MessageDigest.isEqual 사용
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
