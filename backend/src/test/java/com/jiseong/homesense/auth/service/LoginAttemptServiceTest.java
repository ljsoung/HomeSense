package com.jiseong.homesense.auth.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Duration;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;

@ExtendWith(MockitoExtension.class)
class LoginAttemptServiceTest {

    @Mock
    private StringRedisTemplate redisTemplate;
    @Mock
    private ValueOperations<String, String> valueOperations;

    private LoginAttemptService loginAttemptService;

    @Test
    void 실패_카운트가_5_미만이면_잠기지_않는다() {
        loginAttemptService = new LoginAttemptService(redisTemplate);
        when(redisTemplate.opsForValue()).thenReturn(valueOperations);
        when(valueOperations.get("login:fail:user@test.com")).thenReturn("4");

        assertThat(loginAttemptService.isLocked("user@test.com")).isFalse();
    }

    @Test
    void 실패_카운트가_5_이상이면_잠긴다() {
        loginAttemptService = new LoginAttemptService(redisTemplate);
        when(redisTemplate.opsForValue()).thenReturn(valueOperations);
        when(valueOperations.get("login:fail:user@test.com")).thenReturn("5");

        assertThat(loginAttemptService.isLocked("user@test.com")).isTrue();
    }

    @Test
    void 카운트가_없으면_잠기지_않는다() {
        loginAttemptService = new LoginAttemptService(redisTemplate);
        when(redisTemplate.opsForValue()).thenReturn(valueOperations);
        when(valueOperations.get("login:fail:new@test.com")).thenReturn(null);

        assertThat(loginAttemptService.isLocked("new@test.com")).isFalse();
    }

    @Test
    void 실패를_기록하면_증가시키고_5분_TTL을_다시_건다() {
        loginAttemptService = new LoginAttemptService(redisTemplate);
        when(redisTemplate.opsForValue()).thenReturn(valueOperations);

        loginAttemptService.recordFailure("user@test.com");

        verify(valueOperations).increment("login:fail:user@test.com");
        verify(redisTemplate).expire(eq("login:fail:user@test.com"), eq(Duration.ofMinutes(5)));
    }

    @Test
    void 초기화하면_카운터_키를_삭제한다() {
        loginAttemptService = new LoginAttemptService(redisTemplate);

        loginAttemptService.reset("user@test.com");

        verify(redisTemplate).delete("login:fail:user@test.com");
    }
}
