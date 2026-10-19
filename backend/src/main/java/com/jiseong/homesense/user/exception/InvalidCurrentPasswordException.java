package com.jiseong.homesense.user.exception;

import org.springframework.http.HttpStatus;

import com.jiseong.homesense.common.exception.BusinessException;

/**
 * SVC-USER-01.updateUser() — 비밀번호 변경의 현재 비밀번호가 저장된 값과 다르다. 설계서 3.2절은 InvalidCredentialsException
 * (401)이지만 400으로 둔다: 인증된 요청의 401은 "토큰이 무효"로 읽혀 세션 만료 처리와 섞일 수 있다(CLAUDE.md "SVC-USER-01
 * 회원정보 수정 보강" 절 D2). 로그인·탈퇴의 InvalidCredentialsException(401)은 그대로다.
 */
public class InvalidCurrentPasswordException extends BusinessException {

    public InvalidCurrentPasswordException() {
        super("INVALID_CURRENT_PASSWORD", "현재 비밀번호가 일치하지 않습니다", HttpStatus.BAD_REQUEST);
    }
}
