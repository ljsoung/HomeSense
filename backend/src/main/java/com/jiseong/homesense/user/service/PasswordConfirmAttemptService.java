package com.jiseong.homesense.user.service;

import java.util.List;

import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.data.redis.core.script.RedisScript;
import org.springframework.stereotype.Component;

import com.jiseong.homesense.common.security.FailedAttemptPolicy;

/**
 * SVC-USER-01 부가 기능 — 로그인한 회원의 비밀번호 확인 시도를 Redis 키 {@code password-confirm:fail:{userId}} 하나로 센다.
 * 비밀번호 변경(updateUser()의 현재 비밀번호)과 탈퇴(withdraw()의 비밀번호 재확인)가 같은 카운터를 공유한다 — 둘 중 하나라도
 * 잠금 없이 남으면, 탈취된 Access Token(최대 30분)으로 그 경로에서 비밀번호를 맞힌 뒤 다른 경로(비밀번호 변경, 또는 탈퇴 후
 * /api/auth/reactivate)로 계정을 가져갈 수 있다. 임계치·TTL은 로그인 실패 잠금과 같다({@link FailedAttemptPolicy}).
 * 카운터는 트랜잭션과 무관하게 남는다 — 실패 응답으로 롤백돼도 증가한 횟수는 유지돼야 한다.
 *
 * <p>시도는 비밀번호를 비교하기 <b>전에</b> {@link #tryAcquire}로 한 번에 확인·예약한다(Lua 스크립트 하나 — 조회와 증가 사이에
 * 다른 요청이 끼지 못한다). "잠금 확인 → BCrypt 비교 → 실패 기록"으로 나눠 두면 BCrypt 비교 시간 동안 같은 회원의 동시 요청이 모두
 * 잠금 확인을 통과해, 임계치(5회)를 넘는 추측이 동시에 평가됐다(코드리뷰 P2).
 *
 * <p>예약한 시도는 성공·실패와 무관하게 센다 — 성공해도 카운터를 지우지 않고, 잠금은 TTL 만료로만 풀린다. 비밀번호 확인 성공은
 * 곧 모든 세션 종료(비밀번호 변경) 또는 계정 비활성화(탈퇴)로 이어져, 성공 직후 같은 회원이 다시 확인할 일이 거의 없다. 성공 시
 * 초기화는 그 이점 없이 성공과 동시 실패 사이의 경쟁만 만들었다 — 맞는 비밀번호를 비교하는 동안 예약된 틀린 시도까지 지워져 곧바로
 * 5건을 더 비교할 수 있었다(코드리뷰 P2). 무효화 조건: 성공 후에도 세션이 유지되는 비밀번호 확인 기능이 생기는 경우.
 */
@Component
class PasswordConfirmAttemptService {

    private static final String KEY_PREFIX = "password-confirm:fail:";

    /** 카운트가 임계치 미만이면 1 증가시키고 TTL을 다시 건 뒤 1, 이미 임계치 이상이면 아무것도 바꾸지 않고 0. */
    private static final RedisScript<Long> ACQUIRE_SCRIPT = new DefaultRedisScript<>("""
            local count = tonumber(redis.call('GET', KEYS[1]) or '0')
            if count >= tonumber(ARGV[1]) then
              return 0
            end
            redis.call('INCR', KEYS[1])
            redis.call('PEXPIRE', KEYS[1], ARGV[2])
            return 1
            """, Long.class);

    private final StringRedisTemplate redisTemplate;

    PasswordConfirmAttemptService(StringRedisTemplate redisTemplate) {
        this.redisTemplate = redisTemplate;
    }

    /**
     * 시도 하나를 예약한다. 잠겨 있으면(TTL 안에 예약된 시도가 이미 {@link FailedAttemptPolicy#MAX_ATTEMPTS}회) false이고 카운터·TTL은
     * 그대로다(잠긴 뒤의 시도가 잠금을 늘리지 않는다 — 예전 동작과 같다). true면 이 시도가 카운트에 들어가고 TTL이
     * {@link FailedAttemptPolicy#LOCK_TTL}로 다시 걸린다.
     */
    boolean tryAcquire(Long userId) {
        Long acquired = redisTemplate.execute(ACQUIRE_SCRIPT, List.of(key(userId)),
                String.valueOf(FailedAttemptPolicy.MAX_ATTEMPTS),
                String.valueOf(FailedAttemptPolicy.LOCK_TTL.toMillis()));
        return acquired != null && acquired == 1L;
    }

    private String key(Long userId) {
        return KEY_PREFIX + userId;
    }
}
