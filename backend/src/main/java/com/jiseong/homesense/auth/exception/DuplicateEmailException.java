package com.jiseong.homesense.auth.exception;

import org.springframework.http.HttpStatus;

import com.jiseong.homesense.common.exception.BusinessException;

/** SVC-AUTH-01.signup() — 이미 가입된 이메일로 재가입을 시도한 경우. */
public class DuplicateEmailException extends BusinessException {

    public DuplicateEmailException() {
        super("DUPLICATE_EMAIL", "이미 사용 중인 이메일입니다", HttpStatus.CONFLICT);
    }
}
