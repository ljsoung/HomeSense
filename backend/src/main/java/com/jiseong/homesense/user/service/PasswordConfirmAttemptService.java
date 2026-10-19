package com.jiseong.homesense.user.service;

import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

import com.jiseong.homesense.common.security.FailedAttemptPolicy;

/**
 * SVC-USER-01 부가 기능 — 로그인한 회원의 비밀번호 확인 실패를 Redis 키 {@code password-confirm:fail:{userId}} 하나로 센다.
 * 비밀번호 변경(updateUser()의 현재 비밀번호)과 탈퇴(withdraw()의 비밀번호 재확인)가 같은 카운터를 공유한다 — 둘 중 하나라도
 * 잠금 없이 남으면, 탈취된 Access Token(최대 30분)으로 그 경로에서 비밀번호를 맞힌 뒤 다른 경로(비밀번호 변경, 또는 탈퇴 후
 * /api/auth/reactivate)로 계정을 가져갈 수 있다. 임계치·TTL은 로그인 실패 잠금과 같다({@link FailedAttemptPolicy},
 * LoginAttemptService와 같은 구현). 카운터는 트랜잭션과 무관하게 남는다 — 실패 응답으로 롤백돼도 증가한 횟수는 유지돼야 한다.
 */
@Component
class PasswordConfirmAttemptService {

    private static final String KEY_PREFIX = "password-confirm:fail:";

    private final StringRedisTemplate redisTemplate;

    PasswordConfirmAttemptService(StringRedisTemplate redisTemplate) {
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
