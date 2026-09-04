package com.jiseong.homesense.common.validation;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Set;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullSource;
import org.junit.jupiter.params.provider.ValueSource;

import jakarta.validation.ConstraintViolation;
import jakarta.validation.Validation;
import jakarta.validation.Validator;
import jakarta.validation.constraints.NotBlank;

class PasswordValidatorTest {

    private final Validator validator = Validation.buildDefaultValidatorFactory().getValidator();

    private record PasswordHolder(@ValidPassword String password) {
    }

    private record NotBlankAndValidPasswordHolder(@NotBlank @ValidPassword String password) {
    }

    @ParameterizedTest
    @ValueSource(strings = {
            "abcd12!@",           // 정확히 8자, 영문+숫자+특수문자
            "Aa1!Aa1!",
            "password1!longer",   // 8자 초과도 허용
    })
    void 영문_숫자_특수문자를_모두_포함한_8자_이상은_유효하다(String password) {
        Set<ConstraintViolation<PasswordHolder>> violations = validator.validate(new PasswordHolder(password));

        assertThat(violations).isEmpty();
    }

    @ParameterizedTest
    @NullSource
    @ValueSource(strings = {
            "",             // 공백
            " ",            // 공백뿐
            "abc12!@",      // 7자 — 길이 미달
            "abcdefgh",     // 영문만
            "12345678",     // 숫자만
            "abcdefg1",     // 영문+숫자, 특수문자 없음
            "!@#$%^&*",     // 특수문자만
    })
    void 조건을_하나라도_충족하지_못하면_무효하다(String password) {
        Set<ConstraintViolation<PasswordHolder>> violations = validator.validate(new PasswordHolder(password));

        assertThat(violations).isNotEmpty();
    }

    @Test
    void 위반_시_기본_메시지가_노출된다() {
        Set<ConstraintViolation<PasswordHolder>> violations = validator.validate(new PasswordHolder("short"));

        assertThat(violations).extracting(ConstraintViolation::getMessage)
                .containsExactly("비밀번호는 8자 이상이며 영문, 숫자, 특수문자를 모두 포함해야 합니다");
    }

    /**
     * 코드리뷰에서 지적된 함정을 회귀 테스트로 고정한다 — {@code @ValidPassword}가 이미 null/공백을
     * 무효로 처리하므로, DTO에 {@code @NotBlank}를 함께 붙이면 공백 입력 하나에 "must not be blank"와
     * 이 애노테이션의 메시지가 중복으로 실린다. SVC-AUTH-01 DTO에는 {@code @ValidPassword} 하나만 쓴다.
     */
    @Test
    void NotBlank와_함께_쓰면_공백_입력에_에러_메시지가_중복으로_실린다() {
        Set<ConstraintViolation<NotBlankAndValidPasswordHolder>> violations =
                validator.validate(new NotBlankAndValidPasswordHolder(""));

        assertThat(violations).hasSize(2);
    }
}
