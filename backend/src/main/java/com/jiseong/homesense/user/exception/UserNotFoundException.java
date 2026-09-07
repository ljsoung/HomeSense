package com.jiseong.homesense.user.exception;

import org.springframework.http.HttpStatus;

import com.jiseong.homesense.common.exception.BusinessException;

/** SVC-USER-01 — 존재하지 않는 회원 ID로 조회/수정/탈퇴를 시도한 경우(토큰 위조 등). */
public class UserNotFoundException extends BusinessException {

    public UserNotFoundException() {
        super("USER_NOT_FOUND", "존재하지 않는 회원입니다", HttpStatus.NOT_FOUND);
    }
}
