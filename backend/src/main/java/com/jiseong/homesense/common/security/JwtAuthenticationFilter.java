package com.jiseong.homesense.common.security;

import java.io.IOException;
import java.util.List;

import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import com.jiseong.homesense.common.exception.RedisUnavailability;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * COM-SEC-01. 요청마다 1회 실행되며 Authorization 헤더의 JWT를 검증해 SecurityContext를 채운다.
 * 비로그인 조회를 전면 허용하는 화면 설계 원칙(UI정의서 1.5절)에 따라, 토큰이 없거나 만료·위조
 * 되었어도 이 필터는 절대 요청을 차단하지 않고 그대로 다음 필터로 진행한다 — 인증이 실제로
 * 필요한 엔드포인트의 401/403은 SecurityConfig의 authorizeHttpRequests가 별도로 반환한다.
 * Refresh Token도 같은 서명 키로 서명되어 validateToken()을 통과하므로, isAccessToken()으로
 * Access Token인지 추가로 확인한 뒤에만 인증 정보를 채운다.
 *
 * <p>구조적으로 유효한 Access Token이어도 {@link UserStatusResolver}가 판단한 계정 상태가
 * ACTIVE가 아니면 인증 정보를 채우지 않는다 — 이 필터는 토큰 클레임만 보고 매 요청 DB를
 * 재조회하지 않으므로, 이 검사가 없으면 탈퇴·정지 직후에도 만료 전까지(최대 accessTokenValidity)
 * 기존 Access Token이 그대로 통용되는 잔여 리스크가 있었다(CLAUDE.md 인증 절 참고). 상태
 * 불일치도 만료·위조 토큰과 같은 패턴으로 처리한다 — 401을 직접 던지지 않고 SecurityContext
 * 설정만 건너뛴 채 필터 체인을 계속 진행한다. Redis 캐시미스일 때 DB로 폴백하는 로직은
 * {@link UserStatusResolver}로 옮겨져 있다 — 이 필터는 캐시 인프라의 존재를 몰라도 된다.
 *
 * <p>계정 status가 그대로 ACTIVE인 채로 기존 Access Token만 무효화해야 하는 경우(예: 비밀번호
 * 재설정 — 코드리뷰 P1 지적)는 status 검사로 잡히지 않는다 — {@link AccessTokenEpochService}가
 * 토큰의 발급 시각({@code iat})과 사용자별 무효화 컷오프를 비교해 이 경우도 같은 패턴(예외 없이
 * SecurityContext 설정만 건너뜀)으로 처리한다.
 *
 * <p><b>Redis 장애 시 강등(2026-10-21).</b> 상태 확인·컷오프 확인은 Redis를 읽는다. Redis 인프라 예외
 * ({@link RedisUnavailability})가 나면 인증 정보를 채우지 않고 비로그인 요청으로 넘긴다 — 공개 조회는 계속 동작하고, 인증이
 * 필요한 API는 {@link RestAuthenticationEntryPoint}가 {@link #AUTH_DEGRADED_ATTRIBUTE}를 보고 401 대신 503을 준다(401이면
 * 프론트 인터셉터가 재발급 → 재시도 → 401로 세션을 끝낼 수 있다). DB로 폴백하지 않는다 — 컷오프(재사용 탐지·비밀번호 변경으로
 * 무효화된 Access Token)가 Redis에만 있어, DB만으로 인증하면 장애 동안 폐기된 토큰이 통과한다. 서명·만료·토큰 종류 검증은 Redis와
 * 무관해 그대로다.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class JwtAuthenticationFilter extends OncePerRequestFilter {

    /** Redis 장애로 인증 확인을 하지 못한 요청에 남기는 속성. 값은 {@link Boolean#TRUE}. */
    public static final String AUTH_DEGRADED_ATTRIBUTE = JwtAuthenticationFilter.class.getName() + ".AUTH_DEGRADED";

    private static final String AUTHORIZATION_HEADER = "Authorization";
    private static final String BEARER_PREFIX = "Bearer ";

    private final JwtTokenProvider jwtTokenProvider;
    private final UserStatusResolver userStatusResolver;
    private final AccessTokenEpochService accessTokenEpochService;

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
            throws ServletException, IOException {
        String token = resolveToken(request);
        if (token != null && jwtTokenProvider.validateToken(token) && jwtTokenProvider.isAccessToken(token)) {
            Long userId = jwtTokenProvider.getUserId(token);
            if (isUsable(token, userId, request)) {
                SecurityContextHolder.getContext().setAuthentication(createAuthentication(token, userId));
            }
        }
        filterChain.doFilter(request, response);
    }

    /** 계정이 ACTIVE이고 토큰이 컷오프 뒤에 발급됐는가. Redis 장애면 false(강등)이고 요청 속성을 남긴다. */
    private boolean isUsable(String token, Long userId, HttpServletRequest request) {
        try {
            return userStatusResolver.isActive(userId)
                    && accessTokenEpochService.isIssuedAfterCutoff(userId, jwtTokenProvider.getIssuedAt(token));
        } catch (RuntimeException e) {
            if (!RedisUnavailability.isRedisUnavailable(e)) {
                throw e;
            }
            request.setAttribute(AUTH_DEGRADED_ATTRIBUTE, Boolean.TRUE);
            // 토큰·이메일·userId는 남기지 않는다.
            log.warn("COM-SEC-01 Redis 장애로 인증 확인을 건너뛴다(비로그인으로 처리): {} {} — {}", request.getMethod(),
                    request.getRequestURI(), e.getClass().getSimpleName());
            return false;
        }
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
