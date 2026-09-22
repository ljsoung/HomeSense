package com.jiseong.homesense.common.security;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Duration;
import java.util.Optional;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;

import com.jiseong.homesense.common.config.JwtProperties;
import com.jiseong.homesense.user.entity.UserStatus;

@ExtendWith(MockitoExtension.class)
class UserStatusCacheServiceTest {

    @Mock
    private StringRedisTemplate redisTemplate;
    @Mock
    private ValueOperations<String, String> valueOperations;

    private UserStatusCacheService service;

    @BeforeEach
    void setUp() {
        // accessTokenValidity=1_800_000ms(30분) — TTL이 Access Token 만료 시각과 정확히 같아야 한다.
        JwtProperties jwtProperties = new JwtProperties("test-secret-0123456789", 1_800_000L, 1_209_600_000L);
        service = new UserStatusCacheService(redisTemplate, jwtProperties);
    }

    @Test
    void setStatus는_user_status_접두어_키로_accessTokenValidity와_같은_TTL로_저장한다() {
        when(redisTemplate.opsForValue()).thenReturn(valueOperations);

        service.setStatus(1L, UserStatus.ACTIVE);

        verify(valueOperations).set("user:status:1", "ACTIVE", Duration.ofMillis(1_800_000L));
    }

    @Test
    void getStatus는_캐시에_값이_있으면_UserStatus로_역직렬화해_반환한다() {
        when(redisTemplate.opsForValue()).thenReturn(valueOperations);
        when(valueOperations.get("user:status:1")).thenReturn("WITHDRAWN");

        Optional<UserStatus> status = service.getStatus(1L);

        assertThat(status).contains(UserStatus.WITHDRAWN);
    }

    @Test
    void getStatus는_캐시미스면_empty를_반환한다() {
        when(redisTemplate.opsForValue()).thenReturn(valueOperations);
        when(valueOperations.get("user:status:1")).thenReturn(null);

        Optional<UserStatus> status = service.getStatus(1L);

        assertThat(status).isEmpty();
    }
}
