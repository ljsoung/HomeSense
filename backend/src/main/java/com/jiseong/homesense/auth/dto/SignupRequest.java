package com.jiseong.homesense.auth.dto;

import com.jiseong.homesense.common.validation.ValidNickname;
import com.jiseong.homesense.common.validation.ValidPassword;

import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/**
 * AUTH-02 회원가입 요청. password/nickname은 각각 {@link ValidPassword}/{@link ValidNickname}이
 * null/공백까지 자체 처리하므로 {@code @NotBlank}를 함께 붙이지 않는다(COM-VAL-01 원칙).
 *
 * <p>email의 {@code @Size(max = 100)}은 {@code user.email VARCHAR(100)}(테이블정의서)과 맞춘 것이다 —
 * 이 제약이 없으면 형식은 유효하지만 100자를 넘는 이메일이 검증을 통과해 INSERT 시점에 DB 컬럼 길이
 * 초과로 실패하고, 그 예외가 별도로 번역되지 않아 400이 아닌 500으로 샌다(코드리뷰에서 지적됨).
 *
 * <p>{@code ageConfirmed}("만 14세 이상입니다" 자기 확인)는 검증 전용이다 — {@link #toCommand()}가
 * 이 값을 {@link SignupCommand}로 넘기지 않으므로 서비스·엔티티·DB·로그 어디에도 남지 않는다.
 * 래퍼 타입 {@code Boolean}인 이유: 원시 {@code boolean}은 필드 누락과 {@code false}를 구분하지 못한다.
 * {@code @AssertTrue}는 null을 통과시키므로 {@code @NotNull}이 반드시 함께 있어야 하고, 둘은 서로 다른
 * 경우(누락 vs false)에만 걸려 한 필드에 중복 에러가 실리지 않는다.
 */
public record SignupRequest(
        @NotBlank @Email @Size(max = 100) String email,
        @ValidPassword String password,
        @ValidNickname String nickname,
        @NotNull(message = AGE_CONFIRMATION_MESSAGE) @AssertTrue(message = AGE_CONFIRMATION_MESSAGE) Boolean ageConfirmed) {

    static final String AGE_CONFIRMATION_MESSAGE = "만 14세 이상 확인이 필요합니다";

    public SignupCommand toCommand() {
        return new SignupCommand(email, password, nickname);
    }
}
