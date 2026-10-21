package com.jiseong.homesense.common.security;

import java.time.Duration;

/**
 * 비밀번호 확인 실패 잠금의 공통 기준 — 로그인({@code login:fail:{email}}, LoginAttemptService)과 로그인한 회원의
 * 비밀번호 확인(비밀번호 변경·탈퇴 공용 {@code password-confirm:fail:{userId}}, PasswordConfirmAttemptService)이 같은 값을 쓴다.
 * 첫 시도부터 {@link #LOCK_TTL} 안에 {@link #MAX_ATTEMPTS}회를 넘는 시도는 비밀번호를 비교하지 않고 거부한다. TTL은 첫 시도 때만 걸려
 * 잠금은 첫 시도로부터 5분이 지나면 카운터가 사라지며 풀린다(고정 창 — 예전의 "실패할 때마다 TTL 다시 걸기"에서 바꿨다,
 * {@link AttemptLimiter}).
 */
public final class FailedAttemptPolicy {

    public static final int MAX_ATTEMPTS = 5;
    public static final Duration LOCK_TTL = Duration.ofMinutes(5);

    private FailedAttemptPolicy() {
    }
}
