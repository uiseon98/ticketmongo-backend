package com.team03.ticketmon._global.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;

/**
 * Spring Security Method-level 보안 설정
 * - @PreAuthorize, @PostAuthorize 어노테이션 활성화
 * - 메서드 레벨에서 권한 기반 접근 제어
 */
@Configuration
@EnableMethodSecurity(prePostEnabled = true)
public class MethodSecurityConfig {

    /**
     * Method Security 활성화
     * - prePostEnabled = true: @PreAuthorize, @PostAuthorize 활성화
     * - securedEnabled = true: @Secured 활성화 (옵션)
     * - jsr250Enabled = true: @RolesAllowed 활성화 (옵션)
     */

    // 추가 설정이 필요한 경우 여기에 Bean 정의
}