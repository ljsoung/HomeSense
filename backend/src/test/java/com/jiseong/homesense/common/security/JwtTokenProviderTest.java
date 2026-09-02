package com.jiseong.homesense.common.security;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

import com.jiseong.homesense.common.config.JwtProperties;

class JwtTokenProviderTest {

    private static final String SECRET = "unit-test-jwt-secret-key-at-least-32-bytes-long";

    private final JwtTokenProvider provider = new JwtTokenProvider(
            new JwtProperties(SECRET, 1_800_000L, 1_209_600_000L));

    @Test
    void AccessToken을_생성하면_검증에_성공하고_클레임을_추출할_수_있다() {
        String token = provider.createAccessToken(1L, "USER");

        assertThat(provider.validateToken(token)).isTrue();
        assertThat(provider.isAccessToken(token)).isTrue();
        assertThat(provider.getUserId(token)).isEqualTo(1L);
        assertThat(provider.getRole(token)).isEqualTo("USER");
    }

    @Test
    void RefreshToken은_role_클레임을_담지_않는다() {
        String token = provider.createRefreshToken(1L);

        assertThat(provider.validateToken(token)).isTrue();
        assertThat(provider.getUserId(token)).isEqualTo(1L);
        assertThat(provider.getRole(token)).isNull();
    }

    @Test
    void RefreshToken은_서명이_유효해도_AccessToken으로_취급되지_않는다() {
        // 회귀 테스트: 두 토큰이 같은 서명 키로 발급되어 validateToken()만으로는 구분되지 않는다
        // — role 유무로 암묵적으로 구분하면 role이 없는 Refresh Token이 "role=null" Access
        // Token처럼 인증에 쓰일 수 있어(ROLE_null), 명시적 type 클레임으로 구분해야 한다.
        String refreshToken = provider.createRefreshToken(1L);

        assertThat(provider.validateToken(refreshToken)).isTrue();
        assertThat(provider.isAccessToken(refreshToken)).isFalse();
    }

    @Test
    void 만료된_토큰은_검증에_실패한다() {
        JwtTokenProvider expiredProvider = new JwtTokenProvider(new JwtProperties(SECRET, -1_000L, -1_000L));
        String token = expiredProvider.createAccessToken(1L, "USER");

        assertThat(provider.validateToken(token)).isFalse();
    }

    @Test
    void 다른_키로_서명된_토큰은_검증에_실패한다() {
        JwtTokenProvider otherProvider = new JwtTokenProvider(
                new JwtProperties("another-unit-test-secret-key-at-least-32-bytes", 1_800_000L, 1_209_600_000L));
        String token = otherProvider.createAccessToken(1L, "USER");

        assertThat(provider.validateToken(token)).isFalse();
    }

    @Test
    void 형식이_올바르지_않은_토큰은_검증에_실패한다() {
        assertThat(provider.validateToken("not-a-jwt")).isFalse();
    }
}
