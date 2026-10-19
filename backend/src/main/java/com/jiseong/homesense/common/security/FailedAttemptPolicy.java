package com.jiseong.homesense.common.security;

import java.time.Duration;

/**
 * 비밀번호 확인 실패 잠금의 공통 기준 — 로그인({@code login:fail:{email}}, LoginAttemptService)과 비밀번호 변경의 현재
 * 비밀번호 확인({@code password-change:fail:{userId}}, PasswordChangeAttemptService)이 같은 값을 쓴다.
 * {@link #MAX_ATTEMPTS}회 이상 실패하면 잠그고, 실패할 때마다 카운터 TTL을 {@link #LOCK_TTL}로 다시 건다(슬라이딩 —
 * 마지막 실패로부터 5분이 지나면 카운터가 사라지며 풀린다).
 */
public final class FailedAttemptPolicy {

    public static final int MAX_ATTEMPTS = 5;
    public static final Duration LOCK_TTL = Duration.ofMinutes(5);

    private FailedAttemptPolicy() {
    }
}
