package com.jiseong.homesense.auth.exception;

import org.springframework.http.HttpStatus;

import com.jiseong.homesense.common.exception.BusinessException;

/** SVC-AUTH-01.reactivate() — 자격 증명은 맞지만 이미 ACTIVE인(탈퇴 상태가 아닌) 계정에 철회를 요청한 경우. */
public class AccountNotWithdrawnException extends BusinessException {

    public AccountNotWithdrawnException() {
        super("ACCOUNT_NOT_WITHDRAWN", "탈퇴 상태의 계정이 아닙니다.", HttpStatus.CONFLICT);
    }
}
