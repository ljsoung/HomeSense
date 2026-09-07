package com.jiseong.homesense.auth.dto;

import com.jiseong.homesense.common.validation.ValidNickname;
import com.jiseong.homesense.common.validation.ValidPassword;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;

/**
 * AUTH-02 회원가입 요청. password/nickname은 각각 {@link ValidPassword}/{@link ValidNickname}이
 * null/공백까지 자체 처리하므로 {@code @NotBlank}를 함께 붙이지 않는다(COM-VAL-01 원칙).
 */
public record SignupRequest(
        @NotBlank @Email String email,
        @ValidPassword String password,
        @ValidNickname String nickname) {

    public SignupCommand toCommand() {
        return new SignupCommand(email, password, nickname);
    }
}
