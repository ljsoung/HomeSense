package com.jiseong.homesense.common.validation;

import jakarta.validation.ConstraintValidator;
import jakarta.validation.ConstraintValidatorContext;

/**
 * ValidNickname의 실제 판정 로직. null/공백은 무효로 처리한다 — {@code @ValidNickname} 하나만
 * 붙여도(별도 {@code @NotBlank} 없이) 그 자체로 완결된 제약이 되도록 하기 위함이다. 길이는
 * {@code codePointCount}로 세어 BMP 밖 문자(서로게이트 쌍)가 2자로 잘못 세어지지 않게 한다.
 */
public class NicknameValidator implements ConstraintValidator<ValidNickname, String> {

    private static final int MIN_LENGTH = 2;
    private static final int MAX_LENGTH = 12;

    @Override
    public boolean isValid(String value, ConstraintValidatorContext context) {
        if (value == null || value.isBlank()) {
            return false;
        }
        int length = value.codePointCount(0, value.length());
        return length >= MIN_LENGTH && length <= MAX_LENGTH;
    }
}
