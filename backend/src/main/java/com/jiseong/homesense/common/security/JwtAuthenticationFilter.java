package com.jiseong.homesense.common.security;

import java.io.IOException;
import java.util.List;

import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import com.jiseong.homesense.user.entity.UserStatus;

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
 * Refresh Token도 같은 서명 키로 서명되어 validateToken()을 통과하므로, isAccessToken()으로
 * Access Token인지 추가로 확인한 뒤에만 인증 정보를 채운다.
 *
 * <p>구조적으로 유효한 Access Token이어도 {@link UserStatusCacheService}에 캐시된 계정 상태가
 * ACTIVE가 아니면(또는 캐시미스면) 인증 정보를 채우지 않는다 — 이 필터는 토큰 클레임만 보고 매
 * 요청 DB를 재조회하지 않으므로, 이 검사가 없으면 탈퇴·정지 직후에도 만료 전까지(최대
 * accessTokenValidity) 기존 Access Token이 그대로 통용되는 잔여 리스크가 있었다(CLAUDE.md 인증
 * 절 참고). 상태 불일치도 만료·위조 토큰과 같은 패턴으로 처리한다 — 401을 직접 던지지 않고
 * SecurityContext 설정만 건너뛴 채 필터 체인을 계속 진행한다.
 */
@Component
@RequiredArgsConstructor
public class JwtAuthenticationFilter extends OncePerRequestFilter {

    private static final String AUTHORIZATION_HEADER = "Authorization";
    private static final String BEARER_PREFIX = "Bearer ";

    private final JwtTokenProvider jwtTokenProvider;
    private final UserStatusCacheService userStatusCacheService;

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
            throws ServletException, IOException {
        String token = resolveToken(request);
        if (token != null && jwtTokenProvider.validateToken(token) && jwtTokenProvider.isAccessToken(token)) {
            Long userId = jwtTokenProvider.getUserId(token);
            if (isActive(userId)) {
                SecurityContextHolder.getContext().setAuthentication(createAuthentication(token, userId));
            }
        }
        filterChain.doFilter(request, response);
    }

    private boolean isActive(Long userId) {
        return userStatusCacheService.getStatus(userId)
                .filter(UserStatus.ACTIVE::equals)
                .isPresent();
    }

    private String resolveToken(HttpServletRequest request) {
        String header = request.getHeader(AUTHORIZATION_HEADER);
        if (header != null && header.startsWith(BEARER_PREFIX)) {
            return header.substring(BEARER_PREFIX.length());
        }
        return null;
    }

    private Authentication createAuthentication(String token, Long userId) {
        String role = jwtTokenProvider.getRole(token);
        UserPrincipal principal = new UserPrincipal(userId, role);
        var authority = new SimpleGrantedAuthority("ROLE_" + role);
        return new UsernamePasswordAuthenticationToken(principal, null, List.of(authority));
    }
}
