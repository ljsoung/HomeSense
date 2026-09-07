package com.jiseong.homesense.auth.exception;

import org.springframework.http.HttpStatus;

import com.jiseong.homesense.common.exception.BusinessException;

/**
 * SVC-AUTH-01.refreshAccessToken()/logout() — Refresh Token이 구조적으로 무효하거나(서명 위조·만료),
 * Access Token이 대신 제출됐거나, DB에 없거나(revoked_yn=true 포함), 만료됐거나, 요청자 소유가 아닌 경우
 * 모두 이 예외 하나로 통일해 재로그인을 유도한다.
 */
public class InvalidRefreshTokenException extends BusinessException {

    public InvalidRefreshTokenException() {
        super("INVALID_REFRESH_TOKEN", "유효하지 않은 Refresh Token입니다. 다시 로그인해주세요", HttpStatus.UNAUTHORIZED);
    }
}
