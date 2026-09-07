package com.jiseong.homesense.auth.dto;

import com.jiseong.homesense.common.validation.ValidNickname;
import com.jiseong.homesense.common.validation.ValidPassword;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * AUTH-02 회원가입 요청. password/nickname은 각각 {@link ValidPassword}/{@link ValidNickname}이
 * null/공백까지 자체 처리하므로 {@code @NotBlank}를 함께 붙이지 않는다(COM-VAL-01 원칙).
 *
 * <p>email의 {@code @Size(max = 100)}은 {@code user.email VARCHAR(100)}(테이블정의서)과 맞춘 것이다 —
 * 이 제약이 없으면 형식은 유효하지만 100자를 넘는 이메일이 검증을 통과해 INSERT 시점에 DB 컬럼 길이
 * 초과로 실패하고, 그 예외가 별도로 번역되지 않아 400이 아닌 500으로 샌다(코드리뷰에서 지적됨).
 */
public record SignupRequest(
        @NotBlank @Email @Size(max = 100) String email,
        @ValidPassword String password,
        @ValidNickname String nickname) {

    public SignupCommand toCommand() {
        return new SignupCommand(email, password, nickname);
    }
}
