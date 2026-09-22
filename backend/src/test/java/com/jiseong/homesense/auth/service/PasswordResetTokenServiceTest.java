package com.jiseong.homesense.auth.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Duration;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;

@ExtendWith(MockitoExtension.class)
class PasswordResetTokenServiceTest {

    @Mock
    private StringRedisTemplate redisTemplate;
    @Mock
    private ValueOperations<String, String> valueOperations;

    /** 결정적 해시 — 실제 SHA-256 구현을 그대로 써서 서비스가 만드는 키와 테스트의 기대 키가 항상 일치하게 한다. */
    private final RefreshTokenHasher hasher = new RefreshTokenHasher();

    private PasswordResetTokenService service;

    @Test
    void issueToken은_무작위_토큰을_반환하고_그_해시를_userId와_함께_30분_TTL로_저장한다() {
        service = new PasswordResetTokenService(redisTemplate, hasher);
        when(redisTemplate.opsForValue()).thenReturn(valueOperations);

        String rawToken = service.issueToken(1L);

        // 32바이트 SecureRandom → hex 64자.
        assertThat(rawToken).hasSize(64).matches("^[0-9a-f]{64}$");

        ArgumentCaptor<String> keyCaptor = ArgumentCaptor.forClass(String.class);
        verify(valueOperations).set(keyCaptor.capture(), eq("1"), eq(Duration.ofMinutes(30)));
        assertThat(keyCaptor.getValue()).isEqualTo("password-reset:token:" + hasher.hash(rawToken));
    }

    @Test
    void issueToken을_두_번_호출하면_서로_다른_토큰을_반환한다() {
        service = new PasswordResetTokenService(redisTemplate, hasher);
        when(redisTemplate.opsForValue()).thenReturn(valueOperations);

        String first = service.issueToken(1L);
        String second = service.issueToken(1L);

        assertThat(first).isNotEqualTo(second);
    }

    @Test
    void peekToken_존재하면_userId를_반환하고_삭제하지_않는다() {
        service = new PasswordResetTokenService(redisTemplate, hasher);
        when(redisTemplate.opsForValue()).thenReturn(valueOperations);
        when(valueOperations.get("password-reset:token:" + hasher.hash("raw"))).thenReturn("42");

        assertThat(service.peekToken("raw")).contains(42L);
        verify(valueOperations, never()).getAndDelete(anyString());
    }

    @Test
    void peekToken_존재하지_않으면_빈_Optional을_반환한다() {
        service = new PasswordResetTokenService(redisTemplate, hasher);
        when(redisTemplate.opsForValue()).thenReturn(valueOperations);
        when(valueOperations.get("password-reset:token:" + hasher.hash("raw"))).thenReturn(null);

        assertThat(service.peekToken("raw")).isEmpty();
    }

    @Test
    void consumeToken은_GETDEL로_원자적으로_조회_후_삭제한다() {
        service = new PasswordResetTokenService(redisTemplate, hasher);
        when(redisTemplate.opsForValue()).thenReturn(valueOperations);
        when(valueOperations.getAndDelete("password-reset:token:" + hasher.hash("raw"))).thenReturn("42");

        assertThat(service.consumeToken("raw")).contains(42L);
    }

    @Test
    void consumeToken_존재하지_않으면_빈_Optional을_반환한다() {
        service = new PasswordResetTokenService(redisTemplate, hasher);
        when(redisTemplate.opsForValue()).thenReturn(valueOperations);
        when(valueOperations.getAndDelete("password-reset:token:" + hasher.hash("raw"))).thenReturn(null);

        assertThat(service.consumeToken("raw")).isEmpty();
    }

    @Test
    void isCoolingDown_키가_있으면_true를_반환한다() {
        service = new PasswordResetTokenService(redisTemplate, hasher);
        when(redisTemplate.hasKey("password-reset:cooldown:user@test.com")).thenReturn(true);

        assertThat(service.isCoolingDown("user@test.com")).isTrue();
    }

    @Test
    void isCoolingDown_키가_없으면_false를_반환한다() {
        service = new PasswordResetTokenService(redisTemplate, hasher);
        when(redisTemplate.hasKey("password-reset:cooldown:user@test.com")).thenReturn(false);

        assertThat(service.isCoolingDown("user@test.com")).isFalse();
    }

    @Test
    void startCooldown은_60초_TTL로_쿨다운_키를_세팅한다() {
        service = new PasswordResetTokenService(redisTemplate, hasher);
        when(redisTemplate.opsForValue()).thenReturn(valueOperations);

        service.startCooldown("user@test.com");

        verify(valueOperations).set("password-reset:cooldown:user@test.com", "1", Duration.ofSeconds(60));
    }
}
