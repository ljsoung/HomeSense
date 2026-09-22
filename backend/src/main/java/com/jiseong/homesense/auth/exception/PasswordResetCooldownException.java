package com.jiseong.homesense.auth.exception;

import org.springframework.http.HttpStatus;

import com.jiseong.homesense.common.exception.BusinessException;

/**
 * SVC-AUTH-01.requestPasswordReset() — 같은 이메일로 재전송 쿨다운(60초) 안에 다시 요청한 경우.
 * {@link AccountLockedException}(로그인 잠금)과 같은 429 패턴이다.
 *
 * <p>쿨다운 키는 계정 존재 여부와 무관하게 항상 세팅된다(AuthService.requestPasswordReset() 참고) —
 * 그래서 이 예외가 던져진다는 사실 자체는 "이 이메일이 몇 초 전에도 요청됐다"만 드러낼 뿐 "그 계정이
 * 실제로 존재한다"는 신호가 되지 않는다. 만약 쿨다운을 계정이 존재할 때만 세팅했다면, 같은 이메일을
 * 빠르게 두 번 제출해 첫 응답과 다른 응답(이 예외)을 받는 것 자체가 계정 존재를 확인하는 오라클이
 * 됐을 것이다.
 */
public class PasswordResetCooldownException extends BusinessException {

    public PasswordResetCooldownException() {
        super("PASSWORD_RESET_COOLDOWN", "잠시 후 다시 시도해주세요", HttpStatus.TOO_MANY_REQUESTS);
    }
}
