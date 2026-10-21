package com.jiseong.homesense.common.security;

import java.time.Duration;
import java.util.List;

import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.data.redis.core.script.RedisScript;
import org.springframework.stereotype.Component;

/**
 * 비밀번호 확인 시도 제한의 공용 저장소 — Redis 카운터 하나에 시도를 원자적으로 예약하고 되돌린다. 로그인 실패 잠금
 * ({@code login:fail:{email}}, LoginAttemptService)과 로그인한 회원의 비밀번호 확인 잠금({@code password-confirm:fail:{userId}},
 * PasswordConfirmAttemptService)이 같이 쓴다. 임계치·TTL은 {@link FailedAttemptPolicy}.
 *
 * <p>비밀번호를 비교하기 <b>전에</b> {@link #reserve}로 예약한다. "잠금 확인 → BCrypt 비교 → 실패 기록"으로 나누면 BCrypt
 * 비교 동안 같은 대상의 동시 요청이 모두 잠금 확인을 통과해 임계치를 넘는 추측이 평가됐다(MY-05 코드리뷰 P2, 로그인도 같은
 * 구조였다). 예약은 Lua 스크립트 한 번이라 증가와 TTL 설정 사이에 실패해도 TTL 없는 카운터가 남지 않는다.
 *
 * <p>TTL은 첫 예약 때만 건다 — 잠금은 "첫 시도부터 TTL 안에 임계치 초과"이고, TTL이 끝나면 카운터가 사라지며 풀린다. 잠긴 뒤의
 * 시도도 카운트는 늘지만 TTL을 늘리지 않으므로 잠금 기간이 연장되지 않는다. TTL이 없는 키(옛 코드가 INCR 뒤 EXPIRE 전에
 * 실패해 남긴 키, 수동 SET 등)는 다음 예약에서 TTL을 받아 스스로 풀린다.
 *
 * <p>무효화 조건: 여러 인스턴스가 서로 다른 Redis를 쓰게 되면 카운터가 인스턴스마다 따로라 임계치가 인스턴스 수만큼 늘어난다.
 */
@Component
public class AttemptLimiter {

    /** 1 증가시키고, 첫 시도이거나 TTL이 없으면 TTL(ms)을 건 뒤 증가한 값을 돌려준다. */
    private static final RedisScript<Long> RESERVE_SCRIPT = new DefaultRedisScript<>("""
            local count = redis.call('INCR', KEYS[1])
            if count == 1 or redis.call('PTTL', KEYS[1]) < 0 then
              redis.call('PEXPIRE', KEYS[1], ARGV[1])
            end
            return count
            """, Long.class);

    /** 값이 0보다 클 때만 1 줄인다. TTL은 건드리지 않는다(DECR은 TTL을 유지한다). 키가 없으면 아무것도 하지 않는다. */
    private static final RedisScript<Long> RELEASE_SCRIPT = new DefaultRedisScript<>("""
            local count = tonumber(redis.call('GET', KEYS[1]) or '0')
            if count > 0 then
              return redis.call('DECR', KEYS[1])
            end
            return count
            """, Long.class);

    private final StringRedisTemplate redisTemplate;

    public AttemptLimiter(StringRedisTemplate redisTemplate) {
        this.redisTemplate = redisTemplate;
    }

    /**
     * 시도 하나를 예약한다. 예약 뒤 카운트가 {@code limit} 이하면 true(이 시도를 평가해도 된다), 넘으면 false(잠김 — 비밀번호를
     * 비교하지 않는다). 거부된 시도도 카운트에 남는다.
     */
    public boolean reserve(String key, int limit, Duration ttl) {
        Long count = redisTemplate.execute(RESERVE_SCRIPT, List.of(key), String.valueOf(ttl.toMillis()));
        // 결과가 없으면(파이프라인·트랜잭션 안 실행 등) 잠긴 것으로 본다 — 비교하지 않는 쪽이 안전하다.
        return count != null && count <= limit;
    }

    /**
     * 이 요청이 예약한 시도 하나만 되돌린다. 키를 지우지 않는다 — 지우면 동시에 진행 중인 다른 요청의 예약까지 사라져, 그 요청들이
     * 평가되는 동안 새 시도가 임계치 없이 들어온다.
     */
    public void release(String key) {
        redisTemplate.execute(RELEASE_SCRIPT, List.of(key));
    }
}
