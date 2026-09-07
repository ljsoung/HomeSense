package com.jiseong.homesense.auth.exception;

import org.springframework.http.HttpStatus;

import com.jiseong.homesense.common.exception.BusinessException;

/** SVC-AUTH-01.login() — status가 WITHDRAWN 또는 SUSPENDED인 계정으로 로그인을 시도한 경우. */
public class AccountNotActiveException extends BusinessException {

    public AccountNotActiveException() {
        super("ACCOUNT_NOT_ACTIVE", "탈퇴하거나 정지된 계정입니다", HttpStatus.FORBIDDEN);
    }
}
