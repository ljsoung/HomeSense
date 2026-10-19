package com.jiseong.homesense.common.exception;

import org.springframework.http.HttpStatus;

/**
 * SVC-AUTH-01 login()·reactivate()의 자격 증명 실패(401 {@code INVALID_CREDENTIALS}). 원래는 auth.exception 소속이었고
 * SVC-USER-01의 비밀번호 변경·탈퇴도 썼지만, 2026-10-19부터 그 둘은 400 {@code INVALID_CURRENT_PASSWORD}
 * (user.exception.InvalidCurrentPasswordException)를 쓴다 — 인증된 요청의 401은 "토큰이 무효"로 읽힌다(CLAUDE.md
 * "SVC-USER-01 회원정보 수정 보강" 절 D2). 공용 위치(common.exception)는 그대로 둔다.
 *
 * <p>이메일이 존재하지 않거나 비밀번호가 일치하지 않는 경우 공통으로 던진다 — 계정 존재 여부가 응답으로 노출되지 않도록 두
 * 상황 모두 동일한 메시지를 쓴다(AUTH-01 예외 처리 원칙). 익명성은 "이메일"이라는 단어가 아니라 두 실패 원인이 같은
 * 메시지를 공유한다는 사실에서 나온다.
 */
public class InvalidCredentialsException extends BusinessException {

    public InvalidCredentialsException() {
        super("INVALID_CREDENTIALS", "비밀번호가 일치하지 않습니다", HttpStatus.UNAUTHORIZED);
    }
}
