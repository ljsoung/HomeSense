package com.jiseong.homesense.auth.exception;

import org.springframework.http.HttpStatus;

import com.jiseong.homesense.common.exception.BusinessException;

/**
 * SVC-AUTH-01.login() — 이메일이 존재하지 않거나 비밀번호가 일치하지 않는 경우 공통으로 던진다.
 * 계정 존재 여부가 응답으로 노출되지 않도록 두 상황 모두 동일한 메시지를 쓴다(AUTH-01 예외 처리 원칙).
 */
public class InvalidCredentialsException extends BusinessException {

    public InvalidCredentialsException() {
        super("INVALID_CREDENTIALS", "이메일 또는 비밀번호가 일치하지 않습니다", HttpStatus.UNAUTHORIZED);
    }
}
