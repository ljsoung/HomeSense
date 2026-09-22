package com.jiseong.homesense.auth.service;

import java.security.SecureRandom;
import java.time.Duration;
import java.util.HexFormat;
import java.util.Optional;

import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

/**
 * SVC-AUTH-01.requestPasswordReset()/resetPassword()/validatePasswordResetToken() 전용 — 재설정
 * 토큰과 재전송 쿨다운을 Redis TTL 키로 관리한다({@code login:fail:{email}}을 쓰는
 * {@link LoginAttemptService}와 같은 선례). 새 DB 테이블(ENT-AUTH-02 등)을 만들지 않기로 한 결정의
 * 근거는 CLAUDE.md AUTH-03 절 참고 — 재설정 토큰은 단발성·단기 유효(30분)라 refresh_token처럼
 * 재사용 탐지·감사 목적의 장기 보관이 필요 없다.
 *
 * <p>토큰 원문은 {@code SecureRandom} 기반 opaque 값(32바이트 hex)이다 — JWT가 아니다(COM-SEC-02와
 * 혼동 금지). DB에 원문을 저장하지 않는 {@link RefreshTokenHasher}와 같은 이유로, Redis 키도 원문이
 * 아니라 SHA-256 해시로 만든다(같은 패키지의 그 해시기를 그대로 재사용 — 범용 문자열 해셔라 재설정
 * 토큰에도 그대로 적용된다).
 */
@Component
class PasswordResetTokenService {

    private static final String TOKEN_KEY_PREFIX = "password-reset:token:";
    private static final String COOLDOWN_KEY_PREFIX = "password-reset:cooldown:";
    private static final Duration TOKEN_TTL = Duration.ofMinutes(30);
    private static final Duration COOLDOWN_TTL = Duration.ofSeconds(60);
    private static final int TOKEN_BYTES = 32;
    private static final SecureRandom SECURE_RANDOM = new SecureRandom();

    private final StringRedisTemplate redisTemplate;
    private final RefreshTokenHasher tokenHasher;

    PasswordResetTokenService(StringRedisTemplate redisTemplate, RefreshTokenHasher tokenHasher) {
        this.redisTemplate = redisTemplate;
        this.tokenHasher = tokenHasher;
    }

    /** 새 토큰을 발급하고 Redis에 {@code userId}를 값으로 저장한다. 반환값(원문)만 이메일 링크에 실린다. */
    String issueToken(Long userId) {
        String rawToken = generateRawToken();
        redisTemplate.opsForValue().set(tokenKey(rawToken), String.valueOf(userId), TOKEN_TTL);
        return rawToken;
    }

    /**
     * 소비하지 않는 조회(GET) — 2단계 입력 폼 진입 전 사전 검증(validate-token) 전용이다. 실제
     * 비밀번호 변경은 {@link #consumeToken}을 써야 한다.
     */
    Optional<Long> peekToken(String rawToken) {
        return parse(redisTemplate.opsForValue().get(tokenKey(rawToken)));
    }

    /**
     * 조회와 삭제를 GETDEL로 원자적으로 묶는다 — 같은 토큰으로 거의 동시에 두 번 재설정을 시도해도
     * 정확히 한쪽만 성공한다(TOCTOU 없음, Redis 단일 커맨드가 직렬화를 보장한다). Refresh Token
     * Rotation의 조건부 UPDATE(affected rows)와 목적은 같지만, Redis는 단일 커맨드 자체가 원자적이라
     * DB처럼 별도 트랜잭션/스냅샷 문제가 애초에 생기지 않는다.
     */
    Optional<Long> consumeToken(String rawToken) {
        return parse(redisTemplate.opsForValue().getAndDelete(tokenKey(rawToken)));
    }

    boolean isCoolingDown(String normalizedEmail) {
        return Boolean.TRUE.equals(redisTemplate.hasKey(cooldownKey(normalizedEmail)));
    }

    /**
     * 계정 존재 여부와 무관하게 항상 호출해야 한다 — {@link com.jiseong.homesense.auth.exception.PasswordResetCooldownException}
     * javadoc 참고(존재하는 계정에만 쿨다운을 걸면 그 자체가 계정 존재를 드러내는 오라클이 된다).
     */
    void startCooldown(String normalizedEmail) {
        redisTemplate.opsForValue().set(cooldownKey(normalizedEmail), "1", COOLDOWN_TTL);
    }

    private Optional<Long> parse(String value) {
        return Optional.ofNullable(value).map(Long::valueOf);
    }

    private String generateRawToken() {
        byte[] bytes = new byte[TOKEN_BYTES];
        SECURE_RANDOM.nextBytes(bytes);
        return HexFormat.of().formatHex(bytes);
    }

    private String tokenKey(String rawToken) {
        return TOKEN_KEY_PREFIX + tokenHasher.hash(rawToken);
    }

    private String cooldownKey(String normalizedEmail) {
        return COOLDOWN_KEY_PREFIX + normalizedEmail;
    }
}
