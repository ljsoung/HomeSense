package com.jiseong.homesense.auth.service;

import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

import com.jiseong.homesense.common.security.AttemptLimiter;
import com.jiseong.homesense.common.security.FailedAttemptPolicy;

/**
 * SVC-AUTH-01.login()·reactivate() 부가 기능 — Redis 키 {@code login:fail:{email}}로 로그인 시도를 센다. 첫 시도부터
 * {@link FailedAttemptPolicy#LOCK_TTL}(5분) 안에 {@link FailedAttemptPolicy#MAX_ATTEMPTS}(5)회를 넘는 시도는 비밀번호를 비교하지
 * 않고 거부한다. 임계치·TTL은 로그인한 회원의 비밀번호 확인 잠금과 같다.
 *
 * <p>시도는 회원 조회와 BCrypt 비교 <b>전에</b> {@link #tryReserve}로 예약한다({@link AttemptLimiter} — Lua 한 번). 예전처럼 "잠금
 * 확인 → 비교 → 실패 기록"으로 나누면 비교 동안 같은 이메일의 동시 요청이 모두 잠금 확인을 통과해 5회를 넘는 추측이 평가됐고,
 * INCR과 EXPIRE가 따로라 그 사이에 실패하면 TTL 없는 카운터가 남아 계정이 영구 잠길 수 있었다. 없는 이메일도 같은 키로 예약한다 —
 * 그래야 잠금 여부로 가입 여부가 드러나지 않는다.
 *
 * <p>토큰을 실제로 발급할 때만 {@link #release}로 자기 예약 하나를 되돌린다. 키를 지우지 않는다 — 지우면 동시에 비교 중인 다른
 * 요청의 예약까지 사라진다. 그래서 성공해도 이전 실패는 TTL까지 남는다.
 */
@Component
class LoginAttemptService {

    private static final String KEY_PREFIX = "login:fail:";

    private final AttemptLimiter attemptLimiter;
    private final StringRedisTemplate redisTemplate;

    LoginAttemptService(AttemptLimiter attemptLimiter, StringRedisTemplate redisTemplate) {
        this.attemptLimiter = attemptLimiter;
        this.redisTemplate = redisTemplate;
    }

    /** 시도 하나를 예약한다. false면 잠겼다(비밀번호를 비교하지 않는다). 거부된 시도도 카운트에 남지만 TTL은 늘지 않는다. */
    boolean tryReserve(String normalizedEmail) {
        return attemptLimiter.reserve(key(normalizedEmail), FailedAttemptPolicy.MAX_ATTEMPTS, FailedAttemptPolicy.LOCK_TTL);
    }

    /** 토큰을 발급한 시도의 예약 하나를 되돌린다. */
    void release(String normalizedEmail) {
        attemptLimiter.release(key(normalizedEmail));
    }

    /**
     * 카운터를 지운다 — 비밀번호 재설정 성공(resetPassword())에서만 쓴다. 이메일로 받은 재설정 토큰을 소비했다는 것이 이미 계정 소유
     * 증명이라, 그 시점에 남은 예약(다른 사람의 추측 포함)을 없애도 된다. 로그인 성공에는 쓰지 않는다({@link #release}).
     */
    void reset(String normalizedEmail) {
        redisTemplate.delete(key(normalizedEmail));
    }

    private String key(String normalizedEmail) {
        return KEY_PREFIX + normalizedEmail;
    }
}
