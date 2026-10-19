package com.jiseong.homesense.user.exception;

import org.springframework.http.HttpStatus;

import com.jiseong.homesense.common.exception.BusinessException;

/** SVC-USER-01 비밀번호 변경·탈퇴 — 비밀번호 확인 실패(두 기능 합산)가 임계치에 닿아 잠겼다(password-confirm:fail:{userId}, D3). */
public class PasswordConfirmLockedException extends BusinessException {

    public PasswordConfirmLockedException() {
        super("PASSWORD_CONFIRM_LOCKED", "비밀번호 확인에 여러 번 실패했습니다. 잠시 후 다시 시도해주세요", HttpStatus.TOO_MANY_REQUESTS);
    }
}
