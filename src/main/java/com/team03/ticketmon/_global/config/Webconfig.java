package com.team03.ticketmon._global.config;

import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.io.ClassPathResource;
import org.springframework.core.io.Resource;
import org.springframework.web.servlet.config.annotation.CorsRegistry;
import org.springframework.web.servlet.config.annotation.ResourceHandlerRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;
import org.springframework.web.servlet.resource.PathResourceResolver;

import java.util.Optional;

@Configuration
@RequiredArgsConstructor
public class WebConfig implements WebMvcConfigurer {

    // CorsProperties를 주입받아 사용합니다.
    private final CorsProperties corsProperties;

    /**
     * React와 같은 SPA(Single Page Application)에서 라우팅을 올바르게 처리하기 위해
     * API 요청이 아닌 모든 경로 요청을 index.html로 리디렉션합니다.
     */
    @Override
    public void addResourceHandlers(ResourceHandlerRegistry registry) {
        registry.addResourceHandler("/**")
                .addResourceLocations("classpath:/static/")
                .resourceChain(true)
                .addResolver(new PathResourceResolver() {
                    @Override
                    protected Resource getResource(String resourcePath, Resource location) {
                        Resource requestedResource = location.createRelative(resourcePath);
                        // 요청된 경로에 파일이 존재하고 읽기 가능하면 해당 리소스를, 그렇지 않으면 index.html을 반환
                        return requestedResource.exists() && requestedResource.isReadable() ? requestedResource
                                : new ClassPathResource("/static/index.html");
                    }
                });
    }

    /**
     * 전역 CORS(Cross-Origin Resource Sharing) 설정을 구성합니다.
     * 프론트엔드와 백엔드 서버가 다른 도메인에서 실행될 때 필요합니다.
     */
    @Override
    public void addCorsMappings(CorsRegistry registry) {
        registry.addMapping("/**") // 모든 경로에 대해 CORS를 적용합니다.
                .allowedOrigins(
                        // application.yml 또는 properties에 정의된 허용 도메인 목록을 가져옵니다.
                        Optional.ofNullable(corsProperties.getAllowedOrigins())
                                .orElse(new String[0])
                )
                .allowedMethods("GET", "POST", "PUT", "DELETE", "PATCH", "OPTIONS") // 허용할 HTTP 메소드를 지정합니다.
                .allowedHeaders("*") // 모든 헤더를 허용합니다.
                .allowCredentials(true) // 쿠키 및 인증 정보를 허용합니다.
                .maxAge(3600); // Pre-flight 요청의 캐시 시간을 설정합니다. (초 단위)
    }
}