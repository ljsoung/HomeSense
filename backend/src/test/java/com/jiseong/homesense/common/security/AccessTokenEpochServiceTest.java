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
    void invalidateTokensIssuedBefore는_user_tokenEpochMs_접두어_키로_밀리초_컷오프를_accessTokenValidity와_같은_TTL로_저장한다() {
        when(redisTemplate.opsForValue()).thenReturn(valueOperations);
        Instant cutoff = Instant.ofEpochMilli(1_700_000_000_734L);

        service.invalidateTokensIssuedBefore(1L, cutoff);

        verify(valueOperations).set("user:tokenEpochMs:1", "1700000000734", Duration.ofMillis(1_800_000L));
    }

    @Test
    void isIssuedAfterCutoff는_컷오프가_없으면_true다() {
        when(redisTemplate.opsForValue()).thenReturn(valueOperations);
        when(valueOperations.get("user:tokenEpochMs:1")).thenReturn(null);

        assertThat(service.isIssuedAfterCutoff(1L, Instant.now())).isTrue();
    }

    @Test
    void isIssuedAfterCutoff는_컷오프와_같은_초라도_그보다_먼저_발급됐으면_false다() {
        // 코드리뷰 P1 — 재사용 탐지와 같은 초에 회전으로 발급된 공격자 토큰. 초 단위 비교에서는 통과했다.
        when(redisTemplate.opsForValue()).thenReturn(valueOperations);
        when(valueOperations.get("user:tokenEpochMs:1")).thenReturn("1700000000734");

        assertThat(service.isIssuedAfterCutoff(1L, Instant.ofEpochMilli(1_700_000_000_120L))).isFalse();
    }

    @Test
    void isIssuedAfterCutoff는_컷오프와_같은_밀리초면_false다() {
        // 막아야 할 토큰은 컷오프보다 먼저 발급되므로 같은 밀리초도 막는 쪽이 안전하다(클래스 javadoc).
        when(redisTemplate.opsForValue()).thenReturn(valueOperations);
        when(valueOperations.get("user:tokenEpochMs:1")).thenReturn("1700000000734");

        assertThat(service.isIssuedAfterCutoff(1L, Instant.ofEpochMilli(1_700_000_000_734L))).isFalse();
    }

    @Test
    void isIssuedAfterCutoff는_컷오프와_같은_초라도_그보다_뒤에_발급됐으면_true다() {
        // 컷오프 직후 같은 초에 정당하게 발급된 토큰은 통과한다(초 단위 절삭 때의 오탐 회귀 방지).
        when(redisTemplate.opsForValue()).thenReturn(valueOperations);
        when(valueOperations.get("user:tokenEpochMs:1")).thenReturn("1700000000734");

        assertThat(service.isIssuedAfterCutoff(1L, Instant.ofEpochMilli(1_700_000_000_735L))).isTrue();
    }
}
