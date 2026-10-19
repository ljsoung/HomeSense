package com.jiseong.homesense.user.service;

import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

import com.jiseong.homesense.common.security.FailedAttemptPolicy;

/**
 * SVC-USER-01.updateUser() 부가 기능 — 비밀번호 변경의 현재 비밀번호 확인 실패를 Redis 키
 * {@code password-change:fail:{userId}}로 센다. 탈취된 Access Token(최대 30분)으로 현재 비밀번호를 무차별 대입해 비밀번호를
 * 바꾸는 것을 막는다. 임계치·TTL은 로그인 실패 잠금과 같다({@link FailedAttemptPolicy}, LoginAttemptService와 같은 구현).
 * 카운터는 트랜잭션과 무관하게 남는다 — 실패 응답으로 updateUser()가 롤백돼도 증가한 횟수는 유지돼야 한다.
 */
@Component
class PasswordChangeAttemptService {

    private static final String KEY_PREFIX = "password-change:fail:";

    private final StringRedisTemplate redisTemplate;

    PasswordChangeAttemptService(StringRedisTemplate redisTemplate) {
        this.redisTemplate = redisTemplate;
    }

    boolean isLocked(Long userId) {
        String value = redisTemplate.opsForValue().get(key(userId));
        return value != null && Integer.parseInt(value) >= FailedAttemptPolicy.MAX_ATTEMPTS;
    }

    void recordFailure(Long userId) {
        String key = key(userId);
        redisTemplate.opsForValue().increment(key);
        redisTemplate.expire(key, FailedAttemptPolicy.LOCK_TTL);
    }

    void reset(Long userId) {
        redisTemplate.delete(key(userId));
    }

    private String key(Long userId) {
        return KEY_PREFIX + userId;
    }
}
