package com.jiseong.homesense.user.exception;

import org.springframework.http.HttpStatus;

import com.jiseong.homesense.common.exception.BusinessException;

/** SVC-USER-01.updateUser() — 현재 비밀번호 확인 실패가 임계치에 닿아 잠겼다(password-change:fail:{userId}, D3). */
public class PasswordChangeLockedException extends BusinessException {

    public PasswordChangeLockedException() {
        super("PASSWORD_CHANGE_LOCKED", "비밀번호 확인에 여러 번 실패했습니다. 잠시 후 다시 시도해주세요", HttpStatus.TOO_MANY_REQUESTS);
    }
}
