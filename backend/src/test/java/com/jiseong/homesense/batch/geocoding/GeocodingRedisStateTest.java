package com.jiseong.homesense.batch.geocoding;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;

import com.jiseong.homesense.common.config.GeocodingProperties;

@ExtendWith(MockitoExtension.class)
class GeocodingRedisStateTest {

    // UTC 15:30 = KST 다음 날 00:30 — 날짜 키가 KST 기준인지 본다
    private static final Clock CLOCK = Clock.fixed(Instant.parse("2026-10-11T15:30:00Z"), ZoneId.of("Asia/Seoul"));

    @Mock
    private StringRedisTemplate redisTemplate;
    @Mock
    private ValueOperations<String, String> valueOps;

    private GeocodingRedisState state;

    @BeforeEach
    void setUp() {
        state = new GeocodingRedisState(redisTemplate, CLOCK, new GeocodingProperties(100, 10_000, 2, 1000, 5, 30, 100, true));
    }

    @Test
    void 못_찾음_표시는_설정한_일수만큼_남는다() {
        when(redisTemplate.opsForValue()).thenReturn(valueOps);

        state.markNotFound(7L, "NO_RESULT");

        verify(valueOps).set("geo:fail:7", "NO_RESULT", Duration.ofDays(30));
    }

    @Test
    void 호출_카운터는_KST_날짜_키로_세고_첫_호출에만_만료를_건다() {
        when(redisTemplate.opsForValue()).thenReturn(valueOps);
        when(valueOps.increment("geo:calls:20261012")).thenReturn(1L, 2L);

        assertThat(state.recordCall()).isEqualTo(1L);
        assertThat(state.recordCall()).isEqualTo(2L);

        verify(redisTemplate).expire("geo:calls:20261012", Duration.ofDays(2));
    }

    @Test
    void 못_찾음_표시가_없으면_건너뛰지_않는다() {
        when(redisTemplate.hasKey("geo:fail:7")).thenReturn(false);

        assertThat(state.isMarkedNotFound(7L)).isFalse();
        verify(redisTemplate, never()).opsForValue();
    }
}
