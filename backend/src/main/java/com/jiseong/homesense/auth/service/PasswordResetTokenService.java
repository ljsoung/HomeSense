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
 *
 * <p>{@code password-reset:active-token:{userId}} 키가 그 사용자의 "현재 유효한 토큰"의 해시를
 * 가리킨다 — {@link #issueToken}이 새 토큰을 발급할 때마다 이 포인터로 이전 토큰을 찾아 함께
 * 무효화한다(P1 코드리뷰 지적: 재전송 쿨다운만으로는 이전에 발급된 토큰이 그대로 30분간 살아있어,
 * 오래된 이메일로도 나중에 계정을 되찾을 수 있는 구멍이었다). {@link #consumeToken}이 성공하면 이
 * 포인터도 함께 지운다.
 */
@Component
class PasswordResetTokenService {

    private static final String TOKEN_KEY_PREFIX = "password-reset:token:";
    private static final String ACTIVE_TOKEN_KEY_PREFIX = "password-reset:active-token:";
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

    /**
     * 새 토큰을 발급하고 Redis에 {@code userId}를 값으로 저장한다. 반환값(원문)만 이메일 링크에 실린다.
     *
     * <p>발급 직전에 이 사용자의 이전 활성 토큰이 있으면 함께 무효화한다 — 그러지 않으면 재전송
     * 쿨다운(60초) 경과 후 다시 요청할 때마다 독립적인 키로 토큰이 쌓여, 오래된 이메일(공유 메일함,
     * 열람 지연 등으로 나중에 읽힐 수 있다)로도 30분 내내 비밀번호를 재설정할 수 있는 구멍이 된다 —
     * 최신 토큰만 유효해야 한다(P1 코드리뷰 지적).
     */
    String issueToken(Long userId) {
        invalidatePreviousToken(userId);

        String rawToken = generateRawToken();
        String hash = tokenHasher.hash(rawToken);
        redisTemplate.opsForValue().set(TOKEN_KEY_PREFIX + hash, String.valueOf(userId), TOKEN_TTL);
        redisTemplate.opsForValue().set(activeTokenKey(userId), hash, TOKEN_TTL);
        return rawToken;
    }

    private void invalidatePreviousToken(Long userId) {
        String previousHash = redisTemplate.opsForValue().get(activeTokenKey(userId));
        if (previousHash != null) {
            redisTemplate.delete(TOKEN_KEY_PREFIX + previousHash);
        }
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
        Optional<Long> userId = parse(redisTemplate.opsForValue().getAndDelete(tokenKey(rawToken)));
        userId.ifPresent(id -> redisTemplate.delete(activeTokenKey(id)));
        return userId;
    }

    /**
     * 쿨다운 확인(GET)과 세팅(SET)을 하나의 SETNX로 원자적으로 묶는다(P2 코드리뷰 지적) — 이전에는
     * {@code isCoolingDown()}으로 먼저 조회하고 통과하면 별도 호출로 {@code startCooldown()}을 실행하는
     * TOCTOU 패턴이었다. 같은 이메일로 동시에 여러 요청이 들어오면 그 조회~세팅 사이의 창에서 전부
     * "쿨다운 없음"을 관측할 수 있어, 60초 제한이 무력화된 채 여러 건이 동시에 통과해 각자
     * {@link PasswordResetNotifier#notifyAsync}(비동기 SES 발송+토큰 발급)를 중복 실행할 수 있었다 —
     * SETNX는 Redis 단일 커맨드라 이 창 자체가 성립하지 않는다({@link #consumeToken}의 GETDEL과 같은
     * 이유). 계정 존재 여부와 무관하게 항상 호출해야 한다 —
     * {@link com.jiseong.homesense.auth.exception.PasswordResetCooldownException} javadoc 참고(존재하는
     * 계정에만 쿨다운을 걸면 그 자체가 계정 존재를 드러내는 오라클이 된다).
     *
     * @return 쿨다운을 획득했으면(=이전에 쿨다운이 없었으면) true, 이미 쿨다운 중이었으면 false
     */
    boolean tryStartCooldown(String normalizedEmail) {
        Boolean acquired = redisTemplate.opsForValue().setIfAbsent(cooldownKey(normalizedEmail), "1", COOLDOWN_TTL);
        return Boolean.TRUE.equals(acquired);
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

    private String activeTokenKey(Long userId) {
        return ACTIVE_TOKEN_KEY_PREFIX + userId;
    }
}
