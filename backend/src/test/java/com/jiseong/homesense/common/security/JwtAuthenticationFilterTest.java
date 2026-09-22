package com.jiseong.homesense.common.security;

import static org.assertj.core.api.Assertions.assertThat;
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
}
