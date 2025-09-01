package com.team03.ticketmon.auth.jwt;

import com.team03.ticketmon.auth.Util.CookieUtil;
import com.team03.ticketmon.auth.service.RefreshTokenService;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.ServletRequest;
import jakarta.servlet.ServletResponse;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.web.filter.GenericFilterBean;

import java.io.IOException;

@RequiredArgsConstructor
public class CustomLogoutFilter extends GenericFilterBean {

    private final JwtTokenProvider jwtTokenProvider;
    private final RefreshTokenService refreshTokenService;
    private final CookieUtil cookieUtil;

    @Override
    public void doFilter(ServletRequest servletRequest, ServletResponse servletResponse, FilterChain filterChain) throws IOException, ServletException {

        HttpServletRequest request = (HttpServletRequest) servletRequest;
        HttpServletResponse response = (HttpServletResponse) servletResponse;

        if (isLogoutRequest(request)) {
            handleLogout(request, response);
        } else {
            filterChain.doFilter(request, response);
        }
    }

    private void handleLogout(HttpServletRequest request, HttpServletResponse response) {
        String refreshToken = jwtTokenProvider.getTokenFromCookies(jwtTokenProvider.CATEGORY_REFRESH, request);
        if (refreshToken == null || refreshToken.isEmpty()) {
            response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
            return;
        }

        try {
            refreshTokenService.validateRefreshToken(refreshToken, true);

            Long userId = jwtTokenProvider.getUserId(refreshToken);

            refreshTokenService.deleteRefreshToken(userId);
            response.setStatus(HttpServletResponse.SC_OK);
        } catch (Exception e) {
            response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
        } finally {
            cookieUtil.deleteJwtCookies(response);
        }
    }

    private boolean isLogoutRequest(HttpServletRequest request) {
        String requestUri = request.getRequestURI();
        String requestMethod = request.getMethod();

        return "/api/auth/logout".equals(requestUri) && "POST".equals(requestMethod);
    }
}
