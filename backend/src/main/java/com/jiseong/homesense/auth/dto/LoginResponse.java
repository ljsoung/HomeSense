package com.jiseong.homesense.auth.dto;

public record LoginResponse(String accessToken, String refreshToken, long expiresIn) {
}
