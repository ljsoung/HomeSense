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

        verify(valueOperations).set(
                eq("password-reset:token:" + hasher.hash(rawToken)), eq("1"), eq(Duration.ofMinutes(30)));
        verify(valueOperations).set(
                eq("password-reset:active-token:1"), eq(hasher.hash(rawToken)), eq(Duration.ofMinutes(30)));
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
    void issueToken은_이전_활성_토큰이_있으면_함께_무효화한다() {
        service = new PasswordResetTokenService(redisTemplate, hasher);
        when(redisTemplate.opsForValue()).thenReturn(valueOperations);
        String previousHash = hasher.hash("previous-raw-token");
        when(valueOperations.get("password-reset:active-token:1")).thenReturn(previousHash);

        service.issueToken(1L);

        verify(redisTemplate).delete("password-reset:token:" + previousHash);
    }

    @Test
    void issueToken은_이전_활성_토큰이_없으면_delete를_호출하지_않는다() {
        service = new PasswordResetTokenService(redisTemplate, hasher);
        when(redisTemplate.opsForValue()).thenReturn(valueOperations);
        when(valueOperations.get("password-reset:active-token:1")).thenReturn(null);

        service.issueToken(1L);

        verify(redisTemplate, never()).delete(anyString());
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
    void consumeToken은_GETDEL로_원자적으로_조회_후_삭제하고_활성_토큰_포인터도_함께_지운다() {
        service = new PasswordResetTokenService(redisTemplate, hasher);
        when(redisTemplate.opsForValue()).thenReturn(valueOperations);
        when(valueOperations.getAndDelete("password-reset:token:" + hasher.hash("raw"))).thenReturn("42");

        assertThat(service.consumeToken("raw")).contains(42L);
        verify(redisTemplate).delete("password-reset:active-token:42");
    }

    @Test
    void consumeToken_존재하지_않으면_빈_Optional을_반환하고_활성_토큰_포인터를_건드리지_않는다() {
        service = new PasswordResetTokenService(redisTemplate, hasher);
        when(redisTemplate.opsForValue()).thenReturn(valueOperations);
        when(valueOperations.getAndDelete("password-reset:token:" + hasher.hash("raw"))).thenReturn(null);

        assertThat(service.consumeToken("raw")).isEmpty();
        verify(redisTemplate, never()).delete(anyString());
    }

    @Test
    void tryStartCooldown은_SETNX로_60초_TTL_쿨다운_키_획득을_시도하고_성공하면_true를_반환한다() {
        service = new PasswordResetTokenService(redisTemplate, hasher);
        when(redisTemplate.opsForValue()).thenReturn(valueOperations);
        when(valueOperations.setIfAbsent("password-reset:cooldown:user@test.com", "1", Duration.ofSeconds(60)))
                .thenReturn(true);

        assertThat(service.tryStartCooldown("user@test.com")).isTrue();
    }

    @Test
    void tryStartCooldown은_이미_쿨다운_중이면_false를_반환한다() {
        // isCoolingDown(GET)과 startCooldown(SET)을 분리하지 않고 SETNX 하나로 묶은 이유(P2 코드리뷰
        // 지적) — 별개 호출이면 그 사이 창에서 동시 요청이 전부 "쿨다운 없음"을 관측해 60초 제한을
        // 우회할 수 있었다.
        service = new PasswordResetTokenService(redisTemplate, hasher);
        when(redisTemplate.opsForValue()).thenReturn(valueOperations);
        when(valueOperations.setIfAbsent("password-reset:cooldown:user@test.com", "1", Duration.ofSeconds(60)))
                .thenReturn(false);

        assertThat(service.tryStartCooldown("user@test.com")).isFalse();
    }
}
