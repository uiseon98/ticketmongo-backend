package com.team03.ticketmon.payment.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.client.reactive.ReactorClientHttpConnector;
import org.springframework.web.reactive.function.client.WebClient;

import io.netty.handler.logging.LogLevel;
import reactor.netty.http.client.HttpClient;
import reactor.netty.transport.logging.AdvancedByteBufFormat;

@Configuration
public class WebClientConfig {

	@Bean
	public WebClient webClient() {
		// 상세한 로그를 보기 위한 HttpClient 설정
		HttpClient httpClient = HttpClient.create()
			// wiretap: 개발 중 외부 API와 주고받는 모든 요청/응답 내용을 상세하게 로그로 출력해줍니다.
			.wiretap(this.getClass().getCanonicalName(), LogLevel.DEBUG, AdvancedByteBufFormat.TEXTUAL);

		return WebClient.builder()
			// 설정한 로깅 기능이 포함된 HttpClient를 WebClient에 연결합니다.
			.clientConnector(new ReactorClientHttpConnector(httpClient))
			.build();
	}
}
