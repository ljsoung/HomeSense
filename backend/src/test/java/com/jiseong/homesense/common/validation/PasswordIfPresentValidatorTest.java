package com.jiseong.homesense.common.validation;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Set;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import jakarta.validation.ConstraintViolation;
import jakarta.validation.Validation;
import jakarta.validation.Validator;

/**
 * SVC-USER-01의 비밀번호 변경(선택적) 시나리오를 검증한다 — {@link ValidPassword}와 정확히 같은
 * 정책이되, null(필드 생략)만은 예외적으로 유효해야 한다는 점이 이 애노테이션의 존재 이유다.
 */
class PasswordIfPresentValidatorTest {

    private final Validator validator = Validation.buildDefaultValidatorFactory().getValidator();

    private record PasswordHolder(@ValidPasswordIfPresent String password) {
    }

    @Test
    void null이면_유효하다() {
        Set<ConstraintViolation<PasswordHolder>> violations = validator.validate(new PasswordHolder(null));

        assertThat(violations).isEmpty();
    }

    @ParameterizedTest
    @ValueSource(strings = {"abcd12!@", "Aa1!Aa1!", "password1!longer"})
    void 값이_있고_정책을_충족하면_유효하다(String password) {
        Set<ConstraintViolation<PasswordHolder>> violations = validator.validate(new PasswordHolder(password));

        assertThat(violations).isEmpty();
    }

    @ParameterizedTest
    @ValueSource(strings = {"", " ", "abc12!@", "abcdefgh", "12345678"})
    void 값이_있는데_정책을_벗어나면_무효하다(String password) {
        Set<ConstraintViolation<PasswordHolder>> violations = validator.validate(new PasswordHolder(password));

        assertThat(violations).isNotEmpty();
    }

    @Test
    void 값이_있고_72바이트를_넘으면_무효하다() {
        String over72Bytes = "Aa1!" + "a".repeat(69);

        Set<ConstraintViolation<PasswordHolder>> violations = validator.validate(new PasswordHolder(over72Bytes));

        assertThat(violations).isNotEmpty();
    }
}
