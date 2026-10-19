package com.jiseong.homesense.user.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Duration;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;

/** 키·임계치·TTL이 로그인 실패 잠금(LoginAttemptServiceTest)과 같은 값인지 본다. */
@ExtendWith(MockitoExtension.class)
class PasswordChangeAttemptServiceTest {

    @Mock
    private StringRedisTemplate redisTemplate;
    @Mock
    private ValueOperations<String, String> valueOperations;

    private PasswordChangeAttemptService service;

    @BeforeEach
    void setUp() {
        service = new PasswordChangeAttemptService(redisTemplate);
    }

    @Test
    void 실패_카운트가_5_미만이면_잠기지_않는다() {
        when(redisTemplate.opsForValue()).thenReturn(valueOperations);
        when(valueOperations.get("password-change:fail:7")).thenReturn("4");

        assertThat(service.isLocked(7L)).isFalse();
    }

    @Test
    void 실패_카운트가_5_이상이면_잠긴다() {
        when(redisTemplate.opsForValue()).thenReturn(valueOperations);
        when(valueOperations.get("password-change:fail:7")).thenReturn("5");

        assertThat(service.isLocked(7L)).isTrue();
    }

    @Test
    void 카운트가_없으면_잠기지_않는다() {
        when(redisTemplate.opsForValue()).thenReturn(valueOperations);
        when(valueOperations.get("password-change:fail:7")).thenReturn(null);

        assertThat(service.isLocked(7L)).isFalse();
    }

    @Test
    void 실패를_기록하면_증가시키고_5분_TTL을_다시_건다() {
        when(redisTemplate.opsForValue()).thenReturn(valueOperations);

        service.recordFailure(7L);

        verify(valueOperations).increment("password-change:fail:7");
        verify(redisTemplate).expire(eq("password-change:fail:7"), eq(Duration.ofMinutes(5)));
    }

    @Test
    void 초기화하면_카운터_키를_삭제한다() {
        service.reset(7L);

        verify(redisTemplate).delete("password-change:fail:7");
    }
}
