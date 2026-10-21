package com.jiseong.homesense.auth.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Duration;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.redis.core.StringRedisTemplate;

import com.jiseong.homesense.common.security.AttemptLimiter;

/**
 * 키·임계치·TTL이 공용 제한기에 그대로 넘어가는지 본다. 판정 경계(5번째 허용, 6번째 거부)는 AttemptLimiterTest, 원자성·TTL
 * 복구는 실제 Redis를 쓰는 AuthServiceLoginAttemptMariaDbIT가 본다.
 */
@ExtendWith(MockitoExtension.class)
class LoginAttemptServiceTest {

    @Mock
    private AttemptLimiter attemptLimiter;
    @Mock
    private StringRedisTemplate redisTemplate;

    private LoginAttemptService loginAttemptService;

    @BeforeEach
    void setUp() {
        loginAttemptService = new LoginAttemptService(attemptLimiter, redisTemplate);
    }

    @Test
    void 예약은_login_fail_키와_임계치_5_TTL_5분으로_제한기를_부른다() {
        when(attemptLimiter.reserve("login:fail:user@test.com", 5, Duration.ofMinutes(5))).thenReturn(true, false);

        assertThat(loginAttemptService.tryReserve("user@test.com")).isTrue();
        assertThat(loginAttemptService.tryReserve("user@test.com")).isFalse();
    }

    @Test
    void 되돌리기는_자기_예약_하나만_줄이고_키를_지우지_않는다() {
        loginAttemptService.release("user@test.com");

        verify(attemptLimiter).release("login:fail:user@test.com");
        verify(redisTemplate, never()).delete(anyString());
    }

    @Test
    void 초기화는_카운터_키를_삭제한다() {
        loginAttemptService.reset("user@test.com");

        verify(redisTemplate).delete("login:fail:user@test.com");
    }
}
