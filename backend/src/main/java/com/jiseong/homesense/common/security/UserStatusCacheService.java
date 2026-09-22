package com.jiseong.homesense.common.security;

import java.time.Duration;
import java.util.Optional;

import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

import com.jiseong.homesense.common.config.JwtProperties;
import com.jiseong.homesense.user.entity.UserStatus;

/**
 * COM-SEC-01/02 잔여 리스크 대응 — Redis 키 {@code user:status:{userId}}에 현재 계정 상태를 캐싱해
 * {@link JwtAuthenticationFilter}가 매 요청 DB를 재조회하지 않고도 탈퇴·정지 직후의 Access Token을
 * 사실상 즉시 무효화할 수 있게 한다(CLAUDE.md 인증 절 "알려진 잔여 리스크" 참고).
 *
 * <p>TTL은 Access Token 만료 시각과 정확히 같게 잡는다 — 캐시가 먼저 만료돼도 그 시점엔 Access
 * Token 자체도 이미 만료라 안전하고, 그보다 짧으면 정상 사용자도 만료 전에 캐시미스로
 * 미인증 처리되는 조용한 회귀가 생긴다.
 *
 * <p>Repository를 직접 의존하지 않는다 — 이 클래스는 Redis 캐시 하나만 다루고, 계정 상태를 DB에서
 * 읽어 캐시에 써넣는 책임은 각 쓰기 지점(AuthService/UserService)에 있다(계층 원칙).
 */
@Component
public class UserStatusCacheService {

    private static final String KEY_PREFIX = "user:status:";

    private final StringRedisTemplate redisTemplate;
    private final Duration ttl;

    public UserStatusCacheService(StringRedisTemplate redisTemplate, JwtProperties jwtProperties) {
        this.redisTemplate = redisTemplate;
        this.ttl = Duration.ofMillis(jwtProperties.accessTokenValidity());
    }

    public void setStatus(Long userId, UserStatus status) {
        redisTemplate.opsForValue().set(key(userId), status.name(), ttl);
    }

    /**
     * 캐시 미스는 empty로 표현한다 — 호출부(JwtAuthenticationFilter)는 이를 "상태를 알 수 없음"으로
     * 보고 ACTIVE가 아닌 것과 동일하게(미인증) 처리한다.
     */
    public Optional<UserStatus> getStatus(Long userId) {
        String value = redisTemplate.opsForValue().get(key(userId));
        return Optional.ofNullable(value).map(UserStatus::valueOf);
    }

    private String key(Long userId) {
        return KEY_PREFIX + userId;
    }
}
