package com.jiseong.homesense.auth.dto;

/** SVC-AUTH-01.refreshAccessToken() — Rotation 적용으로 새 Refresh Token도 함께 반환한다(LoginResponse와 필드 순서 통일). */
public record TokenResponse(String accessToken, String refreshToken, long expiresIn) {
}
