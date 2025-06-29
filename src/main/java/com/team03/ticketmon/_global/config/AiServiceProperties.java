package com.team03.ticketmon._global.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Primary;
import lombok.Data;

@Data
@ConfigurationProperties(prefix = "ai.together")
@Primary
public class AiServiceProperties {

	private String apiKey;
	private String apiUrl = "https://api.together.xyz/v1/chat/completions";
	private String model = "meta-llama/Llama-3.3-70B-Instruct-Turbo-Free";
	private Integer timeoutSeconds = 30;
	private Integer maxRetries = 3;
	private Integer maxTokensPerRequest = 100000;     // 요청당 최대 토큰 수
	private Integer maxReviewsPerRequest = 50;        // 요청당 최대 리뷰 수
	private Double charsPerToken = 2.5;               // 문자당 토큰 추정치
	private Double tokenSafetyMargin = 0.2;           // 안전 마진 (20%)

	// 시스템 프롬프트 템플릿
	private String systemPrompt = """
    당신은 콘서트 리뷰를 요약하는 전문가입니다.
    
    **목표**: 20-40대 고객이 콘서트 참석을 결정할 수 있도록 도와주는 요약 작성
    
    **요구사항**:
    1. 3개 섹션으로 구성: "전체 평가", "좋은 점", "아쉬운 점"
    2. 각 섹션은 2-3문장으로 간결하게 작성
    3. 구체적인 사례나 수치가 있으면 포함
    4. 정중한 한국어 종결어미 사용 ("습니다", "했어요")
    5. 전체 200-400자 내외로 작성
    
    **금지사항**:
    - 같은 내용 반복 금지
    - 개별 멤버 평가 금지
    - 불필요한 장황한 설명 금지
    
    간결하고 유용한 정보만 제공하세요.
    """;
}