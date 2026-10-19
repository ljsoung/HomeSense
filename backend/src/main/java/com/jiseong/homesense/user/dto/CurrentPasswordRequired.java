package com.jiseong.homesense.user.dto;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

import jakarta.validation.Constraint;
import jakarta.validation.ConstraintValidator;
import jakarta.validation.ConstraintValidatorContext;
import jakarta.validation.Payload;

/**
 * {@link UpdateUserRequest} 클래스 레벨 검증 — newPassword가 있으면 currentPassword가 비어 있으면 안 된다. 위반은
 * {@code currentPassword} 필드 오류로 나간다(COM-VAL-01 표준 400 {@code VALIDATION_FAILED}, fieldErrors[0].field =
 * "currentPassword"). newPassword가 없으면 currentPassword는 보지 않는다(보내도 무시).
 */
@Documented
@Constraint(validatedBy = CurrentPasswordRequired.Validator.class)
@Target(ElementType.TYPE)
@Retention(RetentionPolicy.RUNTIME)
public @interface CurrentPasswordRequired {

    String message() default "현재 비밀번호를 입력해주세요";

    Class<?>[] groups() default {};

    Class<? extends Payload>[] payload() default {};

    class Validator implements ConstraintValidator<CurrentPasswordRequired, UpdateUserRequest> {

        @Override
        public boolean isValid(UpdateUserRequest request, ConstraintValidatorContext context) {
            if (request == null || request.newPassword() == null) return true;
            if (request.currentPassword() != null && !request.currentPassword().isEmpty()) return true;
            context.disableDefaultConstraintViolation();
            context.buildConstraintViolationWithTemplate(context.getDefaultConstraintMessageTemplate())
                    .addPropertyNode("currentPassword")
                    .addConstraintViolation();
            return false;
        }
    }
}
