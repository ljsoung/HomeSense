package com.jiseong.homesense.auth.dto;

import com.jiseong.homesense.common.validation.ValidPassword;

import jakarta.validation.constraints.NotBlank;

/**
 * AUTH-03 2단계 — 재설정 토큰과 새 비밀번호. {@code newPassword}는 AUTH-02 회원가입과 같은
 * {@link ValidPassword}를 재사용한다(SVC-USER-01.updateUser()의 currentPassword/newPassword와
 * 동일한 정책 재사용 관례) — {@code @NotBlank}를 함께 붙이지 않는다(COM-VAL-01 원칙, null/공백을
 * ValidPassword가 이미 무효로 처리한다).
 */
public record PasswordResetConfirmRequest(@NotBlank String token, @ValidPassword String newPassword) {
}
