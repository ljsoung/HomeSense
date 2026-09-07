package com.jiseong.homesense.auth.dto;

/** 회원가입 + 자동 로그인 결과. 토큰 쌍과 함께 방금 생성된 계정의 요약 정보를 담는다. */
public record SignupResponse(
        String accessToken,
        String refreshToken,
        long expiresIn,
        Long userId,
        String email,
        String nickname) {
}
