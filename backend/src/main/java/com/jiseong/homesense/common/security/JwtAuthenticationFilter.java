package com.jiseong.homesense.common.security;

import java.io.IOException;
import java.util.List;

import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;

/**
 * COM-SEC-01. 요청마다 1회 실행되며 Authorization 헤더의 JWT를 검증해 SecurityContext를 채운다.
 * 비로그인 조회를 전면 허용하는 화면 설계 원칙(UI정의서 1.5절)에 따라, 토큰이 없거나 만료·위조
 * 되었어도 이 필터는 절대 요청을 차단하지 않고 그대로 다음 필터로 진행한다 — 인증이 실제로
 * 필요한 엔드포인트의 401/403은 SecurityConfig의 authorizeHttpRequests가 별도로 반환한다.
 */
@Component
@RequiredArgsConstructor
public class JwtAuthenticationFilter extends OncePerRequestFilter {

    private static final String AUTHORIZATION_HEADER = "Authorization";
    private static final String BEARER_PREFIX = "Bearer ";

    private final JwtTokenProvider jwtTokenProvider;

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
            throws ServletException, IOException {
        String token = resolveToken(request);
        if (token != null && jwtTokenProvider.validateToken(token)) {
            SecurityContextHolder.getContext().setAuthentication(createAuthentication(token));
        }
        filterChain.doFilter(request, response);
    }

    private String resolveToken(HttpServletRequest request) {
        String header = request.getHeader(AUTHORIZATION_HEADER);
        if (header != null && header.startsWith(BEARER_PREFIX)) {
            return header.substring(BEARER_PREFIX.length());
        }
        return null;
    }

    private Authentication createAuthentication(String token) {
        Long userId = jwtTokenProvider.getUserId(token);
        String role = jwtTokenProvider.getRole(token);
        UserPrincipal principal = new UserPrincipal(userId, role);
        var authority = new SimpleGrantedAuthority("ROLE_" + role);
        return new UsernamePasswordAuthenticationToken(principal, null, List.of(authority));
    }
}
