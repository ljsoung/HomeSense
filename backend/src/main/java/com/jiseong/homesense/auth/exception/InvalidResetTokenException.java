package com.jiseong.homesense.auth.exception;

import org.springframework.http.HttpStatus;

import com.jiseong.homesense.common.exception.BusinessException;

/**
 * SVC-AUTH-01.resetPassword()/validatePasswordResetToken() — 재설정 토큰이 만료·미존재·이미 사용됨
 * 중 어느 사유든 전부 이 예외 하나로 통일한다({@link InvalidRefreshTokenException}과 같은 사상: 사유를
 * 구분해 응답하면 공격자에게 토큰 상태에 대한 신호를 준다). 계정이 그 사이 탈퇴·정지된 경우도 같은
 * 예외로 처리한다 — "토큰이 유효하지 않다"는 사용자 입장에서 구분할 필요가 없는 동일한 결과다.
 */
public class InvalidResetTokenException extends BusinessException {

    public InvalidResetTokenException() {
        super("INVALID_RESET_TOKEN", "유효하지 않거나 만료된 재설정 링크입니다. 다시 요청해주세요", HttpStatus.BAD_REQUEST);
    }
}
