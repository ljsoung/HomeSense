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

    /**
     * 무조건 덮어쓴다 — 호출자가 그 순간 DB의 최신 상태를 직접 확정한 경우에만 써야 한다
     * ({@code UserService.withdraw()}처럼 같은 트랜잭션에서 방금 커밋한 상태를 그대로 반영하는
     * 경우). 그렇지 않고 "예전에 읽어 둔 값을 뒤늦게 캐시에 채워 넣는" 경우라면 {@link #setIfAbsent}를
     * 대신 쓰라 — {@link UserStatusResolver}가 이 구분을 정확히 그렇게 하고 있다.
     */
    public void setStatus(Long userId, UserStatus status) {
        redisTemplate.opsForValue().set(key(userId), status.name(), ttl);
    }

    /**
     * 이미 값이 있으면 아무것도 하지 않는다(SETNX) — {@link UserStatusResolver}의 캐시미스 복구
     * 전용이다. 그 복구용 DB 읽기는 언제 일어났는지 알 수 없는 "지연된 읽기"일 수 있어(GC 정지·
     * 스레드 스케줄링 등), 그 사이 더 최신 이벤트(예: 탈퇴)가 이미 무조건 쓰기로 캐시를 채워
     * 놓았다면 그 값을 절대 덮어써서는 안 된다 — {@code setStatus}(무조건 덮어쓰기)를 여기 쓰면
     * 방금 고친 것과 같은 종류의 race가 이 지점에서 재발한다(코드리뷰 지적).
     */
    public boolean setIfAbsent(Long userId, UserStatus status) {
        Boolean result = redisTemplate.opsForValue().setIfAbsent(key(userId), status.name(), ttl);
        return Boolean.TRUE.equals(result);
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
