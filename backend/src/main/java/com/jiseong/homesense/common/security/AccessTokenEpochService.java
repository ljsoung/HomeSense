package com.jiseong.homesense.common.security;

import java.time.Duration;
import java.time.Instant;

import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

import com.jiseong.homesense.common.config.JwtProperties;

/**
 * COM-SEC-01/02 잔여 리스크 대응(2026-09-23, 코드리뷰 P1 지적) — {@code AuthService.resetPassword()}는
 * 기존 Refresh Token을 전부 폐기하지만, 이미 발급된 Access Token은 {@link UserStatusCacheService}가
 * 보는 계정 상태(status)를 전혀 바꾸지 않아(재설정 후에도 계속 ACTIVE) 만료 시각까지(최대
 * {@code accessTokenValidity}, 기본 30분) 그대로 통용됐다 — 비밀번호 재설정이 정확히 막으려는 시나리오
 * (계정 탈취)에서, 공격자가 재설정 이전에 이미 Access Token을 쥐고 있었다면 재설정 이후에도 최대
 * 30분간 계속 인증된 요청을 보낼 수 있는 구멍이었다.
 *
 * <p>Redis 키 {@code user:tokenEpoch:{userId}}에 "이 시각 이전에 발급된 Access Token은 전부 무효"라는
 * 컷오프(epoch)를 기록한다 — 토큰 자체에 상태를 두지 않는 순수 JWT 모델을 유지하면서(COM-SEC-02가
 * 이미 stateless를 전제), {@link JwtAuthenticationFilter}가 매 요청 이 컷오프와 토큰의 {@code iat}
 * 클레임을 비교해 컷오프 이전에 발급된 토큰을 걸러낸다({@link UserStatusResolver}와 같은 형태로
 * 필터에 연결).
 *
 * <p>TTL은 {@link UserStatusCacheService}와 같은 이유로 {@code accessTokenValidity}와 정확히 같다 —
 * 컷오프가 세팅된 시각으로부터 그 기간이 지나면, 컷오프 이전에 발급됐을 수 있는 가장 늦은 토큰(컷오프
 * 직전에 발급된 토큰)조차 이미 자연 만료됐을 것이므로 더 이상 이 키를 들고 있을 필요가 없다.
 *
 * <p>무조건 덮어쓰기(SET)만 제공한다 — {@link UserStatusCacheService#setIfAbsent}가 겪었던 "지연된
 * 캐시미스 복구가 더 최신 값을 덮어쓰는" race는 여기서 성립하지 않는다. 이 서비스는 캐시미스 시
 * DB로 폴백해 값을 복구하는 경로 자체가 없다(값이 없으면 "무효화된 적 없음"으로 해석하면 그만이라
 * 복구할 진실의 원천이 필요 없다) — 그래서 {@link UserStatusResolver}에 대응하는 복구용 컴포넌트도
 * 두지 않았다.
 *
 * <p><b>컷오프·비교 모두 초 단위로 자른다(밀리초 정밀도로 저장하지 않는다) — 실 Redis로 실행한 IT가
 * 잡아낸 결함이다.</b> JWT의 {@code iat}(RFC 7519 NumericDate)는 라이브러리가 직렬화 시점에 초
 * 단위로 자른다({@link JwtTokenProvider}의 {@code jti} 도입 배경과 같은 특성). 처음 구현은 컷오프를
 * 밀리초 정밀도(`Instant.now()`)로 그대로 저장하고 초 단위로 잘린 {@code iat}과 직접 비교했는데,
 * 이러면 재설정 직후 같은 초 안에 새로 로그인해 발급된(따라서 정당한) Access Token의 {@code iat}이
 * 컷오프보다 초 단위로는 같아도 밀리초로는 여전히 이전으로 비교돼 즉시 걸러지는 오탐이 났다 — 그
 * 토큰은 컷오프 TTL({@code accessTokenValidity}, 최대 30분) 동안 계속 거부되는, 실사용자 로그인이
 * 막히는 회귀였다({@code AuthServicePasswordResetMariaDbIT}의 실 Redis 테스트가 실제로 이 실패를
 * 재현했다 — Mockito 목만으로는 두 쪽 다 자유롭게 밀리초를 넣을 수 있어 이 정밀도 불일치 자체가
 * 드러나지 않았다). 양쪽을 {@link java.time.temporal.ChronoUnit#SECONDS}로 잘라 비교하면 이 오탐이
 * 사라진다 — 대신 컷오프 발생 직전 같은 초 안에 발급된 진짜 stale 토큰이 최대 1초간 걸러지지 않을
 * 수 있는 반대 방향의 작은 여지가 생기는데, "정당한 로그인을 최대 30분 차단"과 "탈취된 토큰을 최대
 * 1초 늦게 차단" 중 후자가 명백히 더 안전한 트레이드오프라 이 폭을 그대로 받아들인다.
 */
@Component
public class AccessTokenEpochService {

    private static final String KEY_PREFIX = "user:tokenEpoch:";

    private final StringRedisTemplate redisTemplate;
    private final Duration ttl;

    public AccessTokenEpochService(StringRedisTemplate redisTemplate, JwtProperties jwtProperties) {
        this.redisTemplate = redisTemplate;
        this.ttl = Duration.ofMillis(jwtProperties.accessTokenValidity());
    }

    /** 호출 시점 이전에 발급된 이 사용자의 모든 Access Token을 무효화한다. */
    public void invalidateTokensIssuedBefore(Long userId, Instant cutoff) {
        redisTemplate.opsForValue().set(key(userId), String.valueOf(cutoff.getEpochSecond()), ttl);
    }

    /**
     * 컷오프가 없으면(무효화된 적 없음) 항상 true다 — {@link JwtAuthenticationFilter}가 이 결과를
     * {@link UserStatusResolver#isActive}와 AND로 묶어 SecurityContext 설정 여부를 결정한다. 비교는
     * 초 단위다(클래스 javadoc의 정밀도 불일치 설명 참고) — {@code issuedAt}은 항상
     * {@link JwtTokenProvider#getIssuedAt}에서 나온, 이미 초 단위로 잘린 값이다.
     */
    public boolean isIssuedAfterCutoff(Long userId, Instant issuedAt) {
        String value = redisTemplate.opsForValue().get(key(userId));
        if (value == null) {
            return true;
        }
        return issuedAt.getEpochSecond() >= Long.parseLong(value);
    }

    private String key(Long userId) {
        return KEY_PREFIX + userId;
    }
}
