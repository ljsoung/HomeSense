package com.jiseong.homesense.common.validation;

import jakarta.validation.ConstraintValidator;
import jakarta.validation.ConstraintValidatorContext;

/**
 * ValidPassword의 실제 판정 로직. null/공백은 무효로 처리한다 — {@code @ValidPassword} 하나만
 * 붙여도(별도 {@code @NotBlank} 없이) 그 자체로 완결된 제약이 되도록 하기 위함이다.
 */
public class PasswordValidator implements ConstraintValidator<ValidPassword, String> {

    private static final int MIN_LENGTH = 8;

    @Override
    public boolean isValid(String value, ConstraintValidatorContext context) {
        if (value == null || value.isBlank() || value.length() < MIN_LENGTH) {
            return false;
        }

        boolean hasLetter = false;
        boolean hasDigit = false;
        boolean hasSpecial = false;
        for (int i = 0; i < value.length(); i++) {
            char c = value.charAt(i);
            if (Character.isLetter(c)) {
                hasLetter = true;
            } else if (Character.isDigit(c)) {
                hasDigit = true;
            } else if (!Character.isWhitespace(c)) {
                hasSpecial = true;
            }
        }
        return hasLetter && hasDigit && hasSpecial;
    }
}
