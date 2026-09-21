package com.jiseong.homesense.auth.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;

/**
 * API-AUTH-01 탈퇴 철회 요청. 탈퇴한 계정은 로그인할 수 없어 인증 전 요청이므로 email+password로 본인을 확인한다.
 * 로그인과 같은 이유로 password 정책은 재검증하지 않는다({@link LoginRequest} 참고).
 */
public record ReactivateRequest(@NotBlank @Email String email, @NotBlank String password) {

    public ReactivateCommand toCommand() {
        return new ReactivateCommand(email, password);
    }
}
