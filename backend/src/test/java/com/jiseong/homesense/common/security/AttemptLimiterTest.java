package com.jiseong.homesense.common.security;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Duration;
import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.RedisScript;

/**
 * 예약 결과(카운트)와 임계치의 경계, 스크립트에 넘기는 키·TTL을 본다. 스크립트 자체(원자성, TTL 없는 키 복구, 0 아래로 내려가지
 * 않음)는 실제 Redis가 필요해 AuthServiceLoginAttemptMariaDbIT가 검증한다.
 */
@ExtendWith(MockitoExtension.class)
class AttemptLimiterTest {

    @Mock
    private StringRedisTemplate redisTemplate;

    private AttemptLimiter limiter;

    @BeforeEach
    void setUp() {
        limiter = new AttemptLimiter(redisTemplate);
    }

    @Test
    @SuppressWarnings("unchecked")
    void 예약은_키와_TTL_밀리초로_스크립트를_한_번_실행한다() {
        when(redisTemplate.execute(any(RedisScript.class), eq(List.of("k")), eq("300000"))).thenReturn(1L);

        assertThat(limiter.reserve("k", 5, Duration.ofMinutes(5))).isTrue();
    }

    @Test
    @SuppressWarnings("unchecked")
    void 카운트가_임계치와_같으면_허용하고_넘으면_거부한다() {
        when(redisTemplate.execute(any(RedisScript.class), any(List.class), any())).thenReturn(5L, 6L);

        assertThat(limiter.reserve("k", 5, Duration.ofMinutes(5))).isTrue();
        assertThat(limiter.reserve("k", 5, Duration.ofMinutes(5))).isFalse();
    }

    @Test
    @SuppressWarnings("unchecked")
    void 결과가_없으면_잠긴_것으로_본다() {
        when(redisTemplate.execute(any(RedisScript.class), any(List.class), any())).thenReturn(null);

        assertThat(limiter.reserve("k", 5, Duration.ofMinutes(5))).isFalse();
    }

    @Test
    @SuppressWarnings("unchecked")
    void 되돌리기는_키를_지우지_않고_스크립트로_한_번_줄인다() {
        limiter.release("k");

        verify(redisTemplate).execute(any(RedisScript.class), eq(List.of("k")));
        verify(redisTemplate, never()).delete(any(String.class));
    }
}
