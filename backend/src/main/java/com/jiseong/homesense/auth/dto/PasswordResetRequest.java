package com.jiseong.homesense.auth.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * AUTH-03 1단계 — 재설정 링크를 받을 이메일. {@code @Size(max = 100)}는 {@code user.email VARCHAR(100)}과
 * 맞춘 것이다(SignupRequest와 동일한 이유 — 컬럼 길이를 넘는 값이 DB까지 내려가 500으로 새는 것을 막는다.
 * 이 요청 자체는 INSERT/UPDATE를 하지 않지만, 조회 조건이 애초에 컬럼에 저장될 수 없는 길이면 항상
 * "계정 없음"과 동일하게 처리되므로 미리 걸러 불필요한 조회를 막는다).
 */
public record PasswordResetRequest(@NotBlank @Email @Size(max = 100) String email) {
}
