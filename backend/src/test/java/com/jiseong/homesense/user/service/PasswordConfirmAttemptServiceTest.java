package com.jiseong.homesense.user.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;

import java.time.Duration;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.jiseong.homesense.common.security.AttemptLimiter;

/**
 * 키·임계치·TTL이 로그인 실패 잠금과 같은 값으로 공용 제한기에 넘어가는지 본다. 스크립트 자체(임계치 판정, 동시 요청)는
 * AttemptLimiterTest와 실제 Redis를 쓰는 UserPasswordConfirmMariaDbIT가 검증한다.
 */
@ExtendWith(MockitoExtension.class)
class PasswordConfirmAttemptServiceTest {

    @Mock
    private AttemptLimiter attemptLimiter;

    private PasswordConfirmAttemptService service;

    @BeforeEach
    void setUp() {
        service = new PasswordConfirmAttemptService(attemptLimiter);
    }

    @Test
    void 시도_예약은_키_임계치_5_TTL_5분으로_제한기를_부른다() {
        when(attemptLimiter.reserve("password-confirm:fail:7", 5, Duration.ofMinutes(5))).thenReturn(true);

        assertThat(service.tryAcquire(7L)).isTrue();
    }

    @Test
    void 제한기가_거부하면_잠긴_것이다() {
        when(attemptLimiter.reserve(anyString(), anyInt(), any())).thenReturn(false);

        assertThat(service.tryAcquire(7L)).isFalse();
    }
}
