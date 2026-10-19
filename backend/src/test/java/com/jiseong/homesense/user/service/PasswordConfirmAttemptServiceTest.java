package com.jiseong.homesense.user.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.RedisScript;

/**
 * 키·임계치·TTL이 로그인 실패 잠금과 같은 값으로 스크립트에 넘어가는지 본다. 스크립트 자체(임계치에서 멈춤, 동시 요청)는
 * 실제 Redis가 필요해 UserPasswordConfirmMariaDbIT가 검증한다.
 */
@ExtendWith(MockitoExtension.class)
class PasswordConfirmAttemptServiceTest {

    @Mock
    private StringRedisTemplate redisTemplate;

    private PasswordConfirmAttemptService service;

    @BeforeEach
    void setUp() {
        service = new PasswordConfirmAttemptService(redisTemplate);
    }

    @Test
    @SuppressWarnings("unchecked")
    void 시도_예약은_키_임계치_5_TTL_5분으로_스크립트를_한_번_실행한다() {
        when(redisTemplate.execute(any(RedisScript.class), eq(List.of("password-confirm:fail:7")), eq("5"),
                eq("300000"))).thenReturn(1L);

        assertThat(service.tryAcquire(7L)).isTrue();
    }

    @Test
    @SuppressWarnings("unchecked")
    void 스크립트가_0을_돌려주면_잠긴_것이다() {
        when(redisTemplate.execute(any(RedisScript.class), any(List.class), any(), any())).thenReturn(0L);

        assertThat(service.tryAcquire(7L)).isFalse();
    }
}
