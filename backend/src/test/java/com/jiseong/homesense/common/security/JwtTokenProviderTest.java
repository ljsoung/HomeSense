package com.jiseong.homesense.common.security;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;

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

    /**
     * 코드리뷰 P2 지적 — jti(무작위 UUID) 없이는 같은 사용자에게 같은 초 안에 발급된 두 Refresh
     * Token의 클레임(sub/type/iat/exp)이 완전히 같아져 서명까지 포함해 바이트 단위로 동일한 문자열이
     * 나온다. Refresh Token은 이 문자열의 해시를 UNIQUE 컬럼(refresh_token.token_value)에 저장하므로,
     * rotation이 연달아 호출되는 상황(클라이언트 재시도, 여러 탭 등)에서 실제 제약 위반으로 이어졌다
     * — "로그인과 재발급 사이엔 보통 수 초~수 분이 있다"는 가정만으로는 막히지 않는 실제 시나리오다.
     */
    @Test
    void 같은_사용자에게_연달아_발급한_RefreshToken은_서로_다르다() {
        String first = provider.createRefreshToken(1L);
        String second = provider.createRefreshToken(1L);

        assertThat(first).isNotEqualTo(second);
    }

    @Test
    void 같은_사용자에게_연달아_발급한_AccessToken도_서로_다르다() {
        String first = provider.createAccessToken(1L, "USER");
        String second = provider.createAccessToken(1L, "USER");

        assertThat(first).isNotEqualTo(second);
    }

    @Test
    void getIssuedAt은_토큰_발급_시각을_추출한다() {
        // AccessTokenEpochService가 이 값을 무효화 컷오프와 비교한다 — iat이 초 단위로 잘리므로
        // "지금"과의 오차가 1초 이내여야 한다.
        Instant before = Instant.now();
        String token = provider.createAccessToken(1L, "USER");
        Instant after = Instant.now();

        Instant issuedAt = provider.getIssuedAt(token);

        assertThat(issuedAt).isBetween(before.minusSeconds(1), after.plusSeconds(1));
    }
}
