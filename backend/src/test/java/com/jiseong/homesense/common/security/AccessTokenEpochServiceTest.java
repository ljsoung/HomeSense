package com.jiseong.homesense.common.security;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Duration;
import java.time.Instant;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;

import com.jiseong.homesense.common.config.JwtProperties;

@ExtendWith(MockitoExtension.class)
class AccessTokenEpochServiceTest {

    @Mock
    private StringRedisTemplate redisTemplate;
    @Mock
    private ValueOperations<String, String> valueOperations;

    private AccessTokenEpochService service;

    @BeforeEach
    void setUp() {
        // accessTokenValidity=1_800_000ms(30분) — TTL이 Access Token 만료 시각과 정확히 같아야 한다
        // (UserStatusCacheService와 같은 근거).
        JwtProperties jwtProperties = new JwtProperties("test-secret-0123456789", 1_800_000L, 1_209_600_000L);
        service = new AccessTokenEpochService(redisTemplate, jwtProperties);
    }

    @Test
    void invalidateTokensIssuedBefore는_user_tokenEpoch_접두어_키로_초_단위_컷오프를_accessTokenValidity와_같은_TTL로_저장한다() {
        // 초 단위로 저장한다 — JWT iat도 초 단위로 잘리므로 양쪽 정밀도를 맞춰야 한다(클래스
        // javadoc의 정밀도 불일치 설명 참고, 밀리초로 저장했다가 실 Redis IT로 잡아낸 회귀).
        when(redisTemplate.opsForValue()).thenReturn(valueOperations);
        Instant cutoff = Instant.ofEpochMilli(1_700_000_000_734L);

        service.invalidateTokensIssuedBefore(1L, cutoff);

        verify(valueOperations).set("user:tokenEpoch:1", "1700000000", Duration.ofMillis(1_800_000L));
    }

    @Test
    void isIssuedAfterCutoff는_컷오프가_없으면_true다() {
        when(redisTemplate.opsForValue()).thenReturn(valueOperations);
        when(valueOperations.get("user:tokenEpoch:1")).thenReturn(null);

        assertThat(service.isIssuedAfterCutoff(1L, Instant.now())).isTrue();
    }

    @Test
    void isIssuedAfterCutoff는_토큰_발급시각이_컷오프보다_이전_초이면_false다() {
        when(redisTemplate.opsForValue()).thenReturn(valueOperations);
        when(valueOperations.get("user:tokenEpoch:1")).thenReturn("1700000000");

        Instant issuedBeforeCutoff = Instant.ofEpochSecond(1_699_999_999L);

        assertThat(service.isIssuedAfterCutoff(1L, issuedBeforeCutoff)).isFalse();
    }

    @Test
    void isIssuedAfterCutoff는_토큰_발급시각이_컷오프_이후_초면_true다() {
        when(redisTemplate.opsForValue()).thenReturn(valueOperations);
        when(valueOperations.get("user:tokenEpoch:1")).thenReturn("1700000000");

        Instant issuedAfterCutoff = Instant.ofEpochSecond(1_700_000_001L);

        assertThat(service.isIssuedAfterCutoff(1L, issuedAfterCutoff)).isTrue();
    }

    @Test
    void isIssuedAfterCutoff는_토큰_발급시각이_컷오프와_같은_초이면_true다() {
        // 컷오프가 세팅된 것과 같은 초 안에(밀리초 단위로는 그 직후에) 정당하게 재발급된 토큰까지
        // 걷어내지 않는다 — 과잉 차단(실사용자 로그인 실패) 방지가 이 서비스의 핵심 트레이드오프다.
        when(redisTemplate.opsForValue()).thenReturn(valueOperations);
        when(valueOperations.get("user:tokenEpoch:1")).thenReturn("1700000000");

        Instant issuedSameSecondAsCutoff = Instant.ofEpochSecond(1_700_000_000L);

        assertThat(service.isIssuedAfterCutoff(1L, issuedSameSecondAsCutoff)).isTrue();
    }
}
