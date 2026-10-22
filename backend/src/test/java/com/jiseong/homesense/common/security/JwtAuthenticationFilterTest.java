package com.jiseong.homesense.common.security;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Instant;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.DataAccessResourceFailureException;
import org.springframework.dao.QueryTimeoutException;
import org.springframework.data.redis.RedisConnectionFailureException;
import org.springframework.mock.web.MockFilterChain;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.core.context.SecurityContextHolder;

@ExtendWith(MockitoExtension.class)
class JwtAuthenticationFilterTest {

    @Mock
    private JwtTokenProvider jwtTokenProvider;
    @Mock
    private UserStatusResolver userStatusResolver;
    @Mock
    private AccessTokenEpochService accessTokenEpochService;

    @InjectMocks
    private JwtAuthenticationFilter filter;

    @AfterEach
    void clearContext() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void Authorization_헤더가_없으면_인증정보를_채우지_않고_통과시킨다() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest();
        MockHttpServletResponse response = new MockHttpServletResponse();
        MockFilterChain chain = new MockFilterChain();

        filter.doFilter(request, response, chain);

        assertThat(SecurityContextHolder.getContext().getAuthentication()).isNull();
        assertThat(chain.getRequest()).isNotNull();
    }

    @Test
    void 유효한_Access_Token이고_상태_확인_결과가_ACTIVE이면_사용자ID와_권한을_SecurityContext에_채운다() throws Exception {
        when(jwtTokenProvider.validateToken("valid-token")).thenReturn(true);
        when(jwtTokenProvider.isAccessToken("valid-token")).thenReturn(true);
        when(jwtTokenProvider.getUserId("valid-token")).thenReturn(1L);
        when(jwtTokenProvider.getRole("valid-token")).thenReturn("ADMIN");
        when(userStatusResolver.isActive(1L)).thenReturn(true);
        Instant issuedAt = Instant.now();
        when(jwtTokenProvider.getIssuedAt("valid-token")).thenReturn(issuedAt);
        when(accessTokenEpochService.isIssuedAfterCutoff(1L, issuedAt)).thenReturn(true);

        MockHttpServletRequest request = new MockHttpServletRequest();
        request.addHeader("Authorization", "Bearer valid-token");
        MockHttpServletResponse response = new MockHttpServletResponse();
        MockFilterChain chain = new MockFilterChain();

        filter.doFilter(request, response, chain);

        var authentication = SecurityContextHolder.getContext().getAuthentication();
        assertThat(authentication).isNotNull();
        assertThat(authentication.getPrincipal()).isEqualTo(new UserPrincipal(1L, "ADMIN"));
        assertThat(authentication.getAuthorities())
                .extracting(Object::toString)
                .containsExactly("ROLE_ADMIN");
    }

    @Test
    void 상태는_ACTIVE여도_컷오프_이전에_발급된_토큰이면_인증정보를_채우지_않는다() throws Exception {
        // 비밀번호 재설정처럼 계정 status는 그대로 ACTIVE인 채 기존 Access Token만 무효화해야
        // 하는 경우(AccessTokenEpochService) — 코드리뷰 P1 지적.
        when(jwtTokenProvider.validateToken("stale-token")).thenReturn(true);
        when(jwtTokenProvider.isAccessToken("stale-token")).thenReturn(true);
        when(jwtTokenProvider.getUserId("stale-token")).thenReturn(1L);
        when(userStatusResolver.isActive(1L)).thenReturn(true);
        Instant issuedAt = Instant.now();
        when(jwtTokenProvider.getIssuedAt("stale-token")).thenReturn(issuedAt);
        when(accessTokenEpochService.isIssuedAfterCutoff(1L, issuedAt)).thenReturn(false);

        MockHttpServletRequest request = new MockHttpServletRequest();
        request.addHeader("Authorization", "Bearer stale-token");
        MockHttpServletResponse response = new MockHttpServletResponse();
        MockFilterChain chain = new MockFilterChain();

        filter.doFilter(request, response, chain);

        assertThat(SecurityContextHolder.getContext().getAuthentication()).isNull();
    }

    /*
     * ACTIVE/WITHDRAWN/SUSPENDED/캐시미스+DB폴백의 세부 분기는 UserStatusResolverTest가 담당한다 —
     * 이 필터는 그 결과(boolean)만 보고 SecurityContext를 채울지 결정하므로, 여기서는 true/false
     * 두 경우만 검증하면 된다.
     */
    @Test
    void 유효한_Access_Token이어도_상태_확인_결과가_false이면_인증정보를_채우지_않는다() throws Exception {
        when(jwtTokenProvider.validateToken("valid-token")).thenReturn(true);
        when(jwtTokenProvider.isAccessToken("valid-token")).thenReturn(true);
        when(jwtTokenProvider.getUserId("valid-token")).thenReturn(1L);
        when(userStatusResolver.isActive(1L)).thenReturn(false);

        MockHttpServletRequest request = new MockHttpServletRequest();
        request.addHeader("Authorization", "Bearer valid-token");
        MockHttpServletResponse response = new MockHttpServletResponse();
        MockFilterChain chain = new MockFilterChain();

        filter.doFilter(request, response, chain);

        assertThat(SecurityContextHolder.getContext().getAuthentication()).isNull();
    }

    @Test
    void Refresh_Token은_서명이_유효해도_인증정보를_채우지_않는다() throws Exception {
        // Refresh Token은 같은 서명 키로 발급되어 validateToken()은 통과하지만 Access Token이
        // 아니므로, 이걸 Authorization 헤더에 실어 보내도 로그인 크리덴셜로 받아들여지면 안 된다.
        when(jwtTokenProvider.validateToken("refresh-token")).thenReturn(true);
        when(jwtTokenProvider.isAccessToken("refresh-token")).thenReturn(false);

        MockHttpServletRequest request = new MockHttpServletRequest();
        request.addHeader("Authorization", "Bearer refresh-token");
        MockHttpServletResponse response = new MockHttpServletResponse();
        MockFilterChain chain = new MockFilterChain();

        filter.doFilter(request, response, chain);

        assertThat(SecurityContextHolder.getContext().getAuthentication()).isNull();
        verify(userStatusResolver, never()).isActive(any());
    }

    @Test
    void 만료되거나_위조된_토큰이면_인증정보를_채우지_않고_통과시킨다() throws Exception {
        when(jwtTokenProvider.validateToken("invalid-token")).thenReturn(false);

        MockHttpServletRequest request = new MockHttpServletRequest();
        request.addHeader("Authorization", "Bearer invalid-token");
        MockHttpServletResponse response = new MockHttpServletResponse();
        MockFilterChain chain = new MockFilterChain();

        filter.doFilter(request, response, chain);

        assertThat(SecurityContextHolder.getContext().getAuthentication()).isNull();
    }

    @Test
    void Bearer_접두어가_없으면_토큰을_추출하지_않는다() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.addHeader("Authorization", "valid-token");
        MockHttpServletResponse response = new MockHttpServletResponse();
        MockFilterChain chain = new MockFilterChain();

        filter.doFilter(request, response, chain);

        assertThat(SecurityContextHolder.getContext().getAuthentication()).isNull();
        verify(jwtTokenProvider, never()).validateToken(any());
    }

    // --- Redis 장애 시 강등(2026-10-21) ---

    private MockHttpServletRequest bearer(String token) {
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/users/me");
        request.addHeader("Authorization", "Bearer " + token);
        return request;
    }

    private void givenValidAccessToken() {
        when(jwtTokenProvider.validateToken("valid-token")).thenReturn(true);
        when(jwtTokenProvider.isAccessToken("valid-token")).thenReturn(true);
        when(jwtTokenProvider.getUserId("valid-token")).thenReturn(1L);
    }

    @Test
    void 상태_확인에서_Redis_연결이_실패하면_인증_없이_통과시키고_강등_속성을_남긴다() throws Exception {
        givenValidAccessToken();
        when(userStatusResolver.isActive(1L)).thenThrow(new RedisConnectionFailureException("redis down"));
        MockHttpServletRequest request = bearer("valid-token");
        MockFilterChain chain = new MockFilterChain();

        filter.doFilter(request, new MockHttpServletResponse(), chain);

        assertThat(SecurityContextHolder.getContext().getAuthentication()).isNull();
        assertThat(chain.getRequest()).isNotNull();
        assertThat(request.getAttribute(JwtAuthenticationFilter.AUTH_DEGRADED_ATTRIBUTE)).isEqualTo(Boolean.TRUE);
    }

    @Test
    void 컷오프_확인에서_Redis_명령이_타임아웃되면_인증_없이_통과시키고_강등_속성을_남긴다() throws Exception {
        givenValidAccessToken();
        when(userStatusResolver.isActive(1L)).thenReturn(true);
        Instant issuedAt = Instant.now();
        when(jwtTokenProvider.getIssuedAt("valid-token")).thenReturn(issuedAt);
        when(accessTokenEpochService.isIssuedAfterCutoff(1L, issuedAt)).thenThrow(new QueryTimeoutException(
                "timeout", new io.lettuce.core.RedisCommandTimeoutException("Command timed out")));
        MockHttpServletRequest request = bearer("valid-token");
        MockFilterChain chain = new MockFilterChain();

        filter.doFilter(request, new MockHttpServletResponse(), chain);

        assertThat(SecurityContextHolder.getContext().getAuthentication()).isNull();
        assertThat(chain.getRequest()).isNotNull();
        assertThat(request.getAttribute(JwtAuthenticationFilter.AUTH_DEGRADED_ATTRIBUTE)).isEqualTo(Boolean.TRUE);
    }

    @Test
    void Redis가_아닌_예외는_강등하지_않고_그대로_던진다() {
        givenValidAccessToken();
        when(userStatusResolver.isActive(1L)).thenThrow(new DataAccessResourceFailureException("db down"));
        MockHttpServletRequest request = bearer("valid-token");

        assertThatThrownBy(() -> filter.doFilter(request, new MockHttpServletResponse(), new MockFilterChain()))
                .isInstanceOf(DataAccessResourceFailureException.class);
        assertThat(request.getAttribute(JwtAuthenticationFilter.AUTH_DEGRADED_ATTRIBUTE)).isNull();
    }

    @Test
    void 위조된_토큰은_Redis를_보지_않아_강등_속성이_없다() throws Exception {
        when(jwtTokenProvider.validateToken("forged-token")).thenReturn(false);
        MockHttpServletRequest request = bearer("forged-token");

        filter.doFilter(request, new MockHttpServletResponse(), new MockFilterChain());

        assertThat(SecurityContextHolder.getContext().getAuthentication()).isNull();
        assertThat(request.getAttribute(JwtAuthenticationFilter.AUTH_DEGRADED_ATTRIBUTE)).isNull();
        verify(userStatusResolver, never()).isActive(any());
    }
}
