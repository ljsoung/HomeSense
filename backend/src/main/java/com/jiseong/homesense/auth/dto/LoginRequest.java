package com.jiseong.homesense.auth.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;

/**
 * AUTH-01 로그인 요청. password는 여기서 정책({@link com.jiseong.homesense.common.validation.ValidPassword})을
 * 재검증하지 않는다 — 정책이 이후 강화되더라도 가입 당시 기준으로 이미 발급된 계정이 로그인 자체를
 * 못 하게 되는 것을 막기 위함이며, 자격 증명 일치 여부는 AuthService가 BCrypt로 판단한다.
 */
public record LoginRequest(@NotBlank @Email String email, @NotBlank String password) {

    public LoginCommand toCommand() {
        return new LoginCommand(email, password);
    }
}
