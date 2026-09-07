package com.jiseong.homesense.auth.dto;

public record TokenResponse(String accessToken, long expiresIn) {
}
