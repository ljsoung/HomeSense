package com.jiseong.homesense.common.validation;

import jakarta.validation.ConstraintValidator;
import jakarta.validation.ConstraintValidatorContext;

/**
 * ValidPasswordIfPresent의 실제 판정 로직. null은 유효로 통과시키고("변경 안 함"), non-null 값만
 * {@link PasswordValidator}에 그대로 위임해 회원가입과 동일한 정책(길이·바이트 상한·문자 조합)을
 * 적용한다.
 */
public class PasswordIfPresentValidator implements ConstraintValidator<ValidPasswordIfPresent, String> {

    private final PasswordValidator delegate = new PasswordValidator();

    @Override
    public boolean isValid(String value, ConstraintValidatorContext context) {
        return value == null || delegate.isValid(value, context);
    }
}
