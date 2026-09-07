package com.jiseong.homesense.auth.service;

import java.time.Duration;

import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

/**
 * SVC-AUTH-01.login() 부가 기능 — Redis 키 {@code login:fail:{email}}로 연속 로그인 실패 횟수를 세어
 * MAX_ATTEMPTS(5)회 이상이면 계정을 잠근다. 실패할 때마다 카운터의 TTL을 LOCK_TTL(5분)로 다시 걸어,
 * 잠금은 "마지막 실패로부터 5분"이 지나면 카운터 자체가 사라지며 자연히 풀린다.
 */
@Component
class LoginAttemptService {

    private static final String KEY_PREFIX = "login:fail:";
    private static final int MAX_ATTEMPTS = 5;
    private static final Duration LOCK_TTL = Duration.ofMinutes(5);

    private final StringRedisTemplate redisTemplate;

    LoginAttemptService(StringRedisTemplate redisTemplate) {
        this.redisTemplate = redisTemplate;
    }

    boolean isLocked(String normalizedEmail) {
        String value = redisTemplate.opsForValue().get(key(normalizedEmail));
        return value != null && Integer.parseInt(value) >= MAX_ATTEMPTS;
    }

    void recordFailure(String normalizedEmail) {
        String key = key(normalizedEmail);
        redisTemplate.opsForValue().increment(key);
        redisTemplate.expire(key, LOCK_TTL);
    }

    void reset(String normalizedEmail) {
        redisTemplate.delete(key(normalizedEmail));
    }

    private String key(String normalizedEmail) {
        return KEY_PREFIX + normalizedEmail;
    }
}
