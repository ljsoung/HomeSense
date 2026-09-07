package com.jiseong.homesense.common.validation;

import jakarta.validation.ConstraintValidator;
import jakarta.validation.ConstraintValidatorContext;

/**
 * ValidNicknameIfPresent의 실제 판정 로직. null은 유효로 통과시키고("변경 안 함"), non-null 값만
 * {@link NicknameValidator}에 그대로 위임해 회원가입과 동일한 2~12자 정책을 적용한다.
 */
public class NicknameIfPresentValidator implements ConstraintValidator<ValidNicknameIfPresent, String> {

    private final NicknameValidator delegate = new NicknameValidator();

    @Override
    public boolean isValid(String value, ConstraintValidatorContext context) {
        return value == null || delegate.isValid(value, context);
    }
}
