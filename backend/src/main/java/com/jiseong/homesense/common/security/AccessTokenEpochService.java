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
 * <p><b>정밀도와 경계 — 밀리초 단위, 발급 시각이 컷오프보다 엄격히 뒤일 때만 통과한다.</b> 이 비교는
 * 두 번 고쳐졌다. (1) 처음엔 컷오프를 밀리초로 저장하고 초 단위로 잘린 JWT {@code iat}과 비교해, 재설정
 * 직후 같은 초에 정당하게 발급된 토큰이 "이전"으로 비교돼 거부됐다({@code AuthServicePasswordResetMariaDbIT}
 * 실 Redis 테스트가 잡음). (2) 그래서 양쪽을 초로 자르고 {@code iat >= cutoff}면 통과시켰는데, 이러면
 * 재사용 탐지와 같은 초에 회전으로 발급된 공격자의 Access Token도 통과해 만료(최대 30분)까지 그대로
 * 쓰였다(코드리뷰 P1) — 공격자 회전과 정상 사용자의 재사용 탐지는 수 밀리초 안에 연달아 일어나는 것이
 * 보통이라 흔한 경우였다. 지금은 {@link JwtTokenProvider#getIssuedAt}이 주는 밀리초 발급 시각
 * ({@code iatMs} 클레임)과 밀리초 컷오프를 비교하고 {@code issuedAt > cutoff}일 때만 통과시킨다.
 *
 * <p>엄격 비교가 안전한 근거: 막아야 할 토큰은 컷오프보다 <i>먼저</i> 발급됐다. 재사용 탐지에서 공격자의
 * Access Token은 {@code RefreshTokenRotator}의 트랜잭션 안(커밋 전)에서 만들어지고, 탐지는 그 커밋 뒤에야
 * 일어난다(패자는 승자의 행 잠금이 풀릴 때까지 기다린다). 그래서 같은 밀리초여도 막힌다. 반대로 컷오프와
 * 같은 밀리초에 정당하게 발급된 토큰도 막히지만, 그 토큰 하나만 거부될 뿐 다음 로그인은 통과한다 — 로그인은
 * 비밀번호 검증(BCrypt)을 거쳐 탐지·재설정보다 한참 뒤에 일어나므로 실제로 겹칠 일도 거의 없다.
 *
 * <p><b>알려진 한계:</b> 발급과 컷오프가 같은 벽시계를 쓴다는 전제다. 여러 인스턴스 사이 시계가 어긋나거나
 * (NTP 보정 등으로) 시계가 뒤로 가면, 컷오프보다 먼저 발급된 토큰의 {@code iatMs}가 컷오프보다 뒤로 기록될 수
 * 있다. 지금은 단일 인스턴스라 해당하지 않는다 — 다중 인스턴스로 가면 발급 세대(generation) 방식이나 공통 시간
 * 소스를 검토하라.
 *
 * <p>키 이름을 {@code user:tokenEpoch:}(초 단위 값)에서 {@code user:tokenEpochMs:}로 바꿨다 — 배포 전
 * 남아 있던 초 단위 값을 밀리초로 잘못 읽으면(1970년 1월) 모든 토큰이 통과한다. 옛 키는 TTL로 사라진다.
 */
@Component
public class AccessTokenEpochService {

    private static final String KEY_PREFIX = "user:tokenEpochMs:";

    private final StringRedisTemplate redisTemplate;
    private final Duration ttl;

    public AccessTokenEpochService(StringRedisTemplate redisTemplate, JwtProperties jwtProperties) {
        this.redisTemplate = redisTemplate;
        this.ttl = Duration.ofMillis(jwtProperties.accessTokenValidity());
    }

    /** 호출 시점 이전에 발급된 이 사용자의 모든 Access Token을 무효화한다. */
    public void invalidateTokensIssuedBefore(Long userId, Instant cutoff) {
        redisTemplate.opsForValue().set(key(userId), String.valueOf(cutoff.toEpochMilli()), ttl);
    }

    /**
     * 컷오프가 없으면(무효화된 적 없음) 항상 true다 — {@link JwtAuthenticationFilter}가 이 결과를
     * {@link UserStatusResolver#isActive}와 AND로 묶어 SecurityContext 설정 여부를 결정한다. 비교는
     * 밀리초 단위이고 컷오프와 같은 밀리초는 막는다(클래스 javadoc 참고) — {@code issuedAt}은
     * {@link JwtTokenProvider#getIssuedAt}에서 나온 값이다.
     */
    public boolean isIssuedAfterCutoff(Long userId, Instant issuedAt) {
        String value = redisTemplate.opsForValue().get(key(userId));
        if (value == null) {
            return true;
        }
        return issuedAt.toEpochMilli() > Long.parseLong(value);
    }

    private String key(Long userId) {
        return KEY_PREFIX + userId;
    }
}
