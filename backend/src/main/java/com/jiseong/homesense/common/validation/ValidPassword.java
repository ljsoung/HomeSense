package com.jiseong.homesense.common.validation;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

import jakarta.validation.Constraint;
import jakarta.validation.Payload;

/**
 * COM-VAL-01. AUTH-02 비밀번호 정책(8자 이상, 영문·숫자·특수문자 조합) — UI 정의서 5.1절과 동일한
 * 규칙을 서버 측에서도 강제해 프론트엔드 검증을 우회한 요청을 막는다(NFR-9, 이중 방어선).
 *
 * <p>{@link PasswordValidator}가 null/공백을 이미 무효로 처리하므로 이 애노테이션 하나로 충분하다 —
 * DTO 필드에 {@code @NotBlank}를 함께 붙이지 마라. 공백을 넣으면 "must not be blank"와 이 애노테이션의
 * 기본 메시지가 같은 필드에 중복으로 실려 나간다(코드리뷰에서 지적됨).
 */
@Documented
@Constraint(validatedBy = PasswordValidator.class)
@Target({ElementType.FIELD, ElementType.PARAMETER})
@Retention(RetentionPolicy.RUNTIME)
public @interface ValidPassword {

    String message() default "비밀번호는 8자 이상이며 영문, 숫자, 특수문자를 모두 포함해야 합니다";

    Class<?>[] groups() default {};

    Class<? extends Payload>[] payload() default {};
}
