package com.jiseong.homesense.auth.exception;

import org.springframework.http.HttpStatus;

import com.jiseong.homesense.common.exception.BusinessException;

/** SVC-AUTH-01.login() — 동일 계정 5회 이상 연속 로그인 실패로 Redis 잠금(login:fail:{email})이 걸린 경우. */
public class AccountLockedException extends BusinessException {

    public AccountLockedException() {
        super("ACCOUNT_LOCKED", "로그인 실패 횟수를 초과했습니다. 잠시 후 다시 시도해주세요", HttpStatus.TOO_MANY_REQUESTS);
    }
}
