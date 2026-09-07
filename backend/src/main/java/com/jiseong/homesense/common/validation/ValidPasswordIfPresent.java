package com.jiseong.homesense.common.validation;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

import jakarta.validation.Constraint;
import jakarta.validation.Payload;

/**
 * COM-VAL-01 확장 — {@link ValidPassword}는 null을 무효로 처리해 회원가입처럼 필수 필드에만 쓸 수
 * 있다. SVC-USER-01의 비밀번호 변경(선택적)처럼 "필드가 없으면 건드리지 않고, 있으면
 * {@link ValidPassword}와 동일한 정책을 적용"해야 하는 경우 이 애노테이션을 쓴다 — null은 유효로
 * 통과시키고, non-null 값만 {@link PasswordValidator}에 그대로 위임한다.
 */
@Documented
@Constraint(validatedBy = PasswordIfPresentValidator.class)
@Target({ElementType.FIELD, ElementType.PARAMETER})
@Retention(RetentionPolicy.RUNTIME)
public @interface ValidPasswordIfPresent {

    String message() default "비밀번호는 8자 이상 72바이트(UTF-8) 이하이며 영문, 숫자, 특수문자를 모두 포함해야 합니다";

    Class<?>[] groups() default {};

    Class<? extends Payload>[] payload() default {};
}
