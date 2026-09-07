package com.jiseong.homesense.common.exception;

import org.springframework.http.HttpStatus;

/**
 * SVC-AUTH-01.login() / SVC-USER-01.updateUser()가 공유한다 — 원래는 auth.exception 소속이었지만
 * SVC-USER-01 설계서가 비밀번호 변경 시 현재 비밀번호 불일치를 같은 이름("InvalidCredentialsException")
 * 그대로 지정해, AUTH 전용이 아니라 여러 도메인이 함께 쓰는 COM-EXC-01 공통 예외로 옮겼다.
 *
 * <p>login()에서는 이메일이 존재하지 않거나 비밀번호가 일치하지 않는 경우 공통으로 던진다 — 계정 존재
 * 여부가 응답으로 노출되지 않도록 두 상황 모두 동일한 메시지를 쓴다(AUTH-01 예외 처리 원칙).
 * updateUser()/withdraw()에서는 제출한 (current)password가 저장된 값과 일치하지 않을 때 던진다.
 *
 * <p>메시지를 "이메일 또는 비밀번호가 일치하지 않습니다"에서 "비밀번호가 일치하지 않습니다"로
 * 좁혔다 — updateUser()/withdraw() 문맥엔 이메일이 등장하지 않아 원래 문구가 어색했다. login()의
 * 익명성 보장(이메일 미존재/비밀번호 불일치를 구분하지 못하게 하는 것)은 "이메일"이라는 단어가
 * 아니라 두 실패 원인이 같은 메시지를 공유한다는 사실 자체에서 나오므로, 이 문구 변경으로도
 * 그 보장은 그대로 유지된다.
 */
public class InvalidCredentialsException extends BusinessException {

    public InvalidCredentialsException() {
        super("INVALID_CREDENTIALS", "비밀번호가 일치하지 않습니다", HttpStatus.UNAUTHORIZED);
    }
}
