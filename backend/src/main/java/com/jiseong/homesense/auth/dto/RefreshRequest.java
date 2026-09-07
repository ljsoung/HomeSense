package com.jiseong.homesense.auth.dto;

import jakarta.validation.constraints.NotBlank;

/** POST /api/auth/refresh, /api/auth/logout 공통 요청 바디. */
public record RefreshRequest(@NotBlank String refreshToken) {
}
