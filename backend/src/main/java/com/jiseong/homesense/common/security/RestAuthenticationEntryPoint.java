package com.jiseong.homesense.common.security;

import java.io.IOException;
import java.time.Instant;

import org.springframework.http.MediaType;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.web.AuthenticationEntryPoint;
import org.springframework.stereotype.Component;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

/**
 * COM-SEC-01. 인증이 필요한 엔드포인트(예: POST /api/auth/logout)에 토큰 없이 접근하면 Spring
 * Security 기본 엔트리포인트(빈 401) 대신 COM-RES-01 표준 에러 포맷으로 응답한다. GlobalExceptionHandler는
 * Controller가 던진 예외만 가로채므로, Security 필터 체인 단계에서 거부되는 이 케이스는 별도로 처리해야
 * 한다.
 */
@Component
public class RestAuthenticationEntryPoint implements AuthenticationEntryPoint {

    @Override
    public void commence(HttpServletRequest request, HttpServletResponse response, AuthenticationException authException)
            throws IOException {
        response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        response.setCharacterEncoding("UTF-8");
        response.getWriter().write("""
                {"success":false,"data":null,"error":{"code":"UNAUTHORIZED","message":"인증이 필요합니다"},"timestamp":"%s"}"""
                .formatted(Instant.now()));
    }
}
