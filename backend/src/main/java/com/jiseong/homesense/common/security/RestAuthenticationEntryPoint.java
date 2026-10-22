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
 *
 * <p>JWT 필터가 Redis 장애로 인증 확인을 건너뛴 요청({@link JwtAuthenticationFilter#AUTH_DEGRADED_ATTRIBUTE})은 401 대신 503
 * {@code AUTH_TEMPORARILY_UNAVAILABLE}이다 — 토큰이 무효인 게 아니라 확인할 수 없는 것이라, 401을 주면 프론트 인터셉터가
 * 재발급을 시도하고 끝내 세션을 끝낼 수 있다. 503이면 프론트는 일시 장애로 보고 토큰을 남긴다.
 */
@Component
public class RestAuthenticationEntryPoint implements AuthenticationEntryPoint {

    @Override
    public void commence(HttpServletRequest request, HttpServletResponse response, AuthenticationException authException)
            throws IOException {
        if (Boolean.TRUE.equals(request.getAttribute(JwtAuthenticationFilter.AUTH_DEGRADED_ATTRIBUTE))) {
            write(response, HttpServletResponse.SC_SERVICE_UNAVAILABLE, "AUTH_TEMPORARILY_UNAVAILABLE",
                    "일시적으로 로그인 상태를 확인할 수 없습니다. 잠시 후 다시 시도해주세요.");
            return;
        }
        write(response, HttpServletResponse.SC_UNAUTHORIZED, "UNAUTHORIZED", "인증이 필요합니다");
    }

    private void write(HttpServletResponse response, int status, String code, String message) throws IOException {
        response.setStatus(status);
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        response.setCharacterEncoding("UTF-8");
        response.getWriter().write("""
                {"success":false,"data":null,"error":{"code":"%s","message":"%s"},"timestamp":"%s"}"""
                .formatted(code, message, Instant.now()));
    }
}
