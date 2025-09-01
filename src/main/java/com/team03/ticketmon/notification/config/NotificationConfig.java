package com.team03.ticketmon.notification.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpHeaders;
import org.springframework.web.reactive.function.client.WebClient;

@Configuration
public class NotificationConfig {

    @Value("${onesignal.api-key}")
    private String oneSignalApiKey;

    @Bean
    public WebClient oneSignalWebClient() {
        return WebClient.builder()
                .baseUrl("https://onesignal.com/api/v1")
                .defaultHeader(HttpHeaders.AUTHORIZATION, "Basic " + oneSignalApiKey)
                .build();
    }
}
