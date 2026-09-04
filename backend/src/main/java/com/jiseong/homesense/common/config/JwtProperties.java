package com.jiseong.homesense.common.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

import jakarta.validation.constraints.NotBlank;

/**
 * COM-SEC-02. homesense.security.jwt.* 설정을 바인딩한다.
 * secret은 JWT_SECRET 환경변수(.env)에서 오며 코드에는 절대 하드코딩하지 않는다(NFR-4).
 * accessTokenValidity/refreshTokenValidity 단위는 밀리초다.
 *
 * <p>COM-CFG-01 — 환경변수가 빈 문자열로 설정된 경우 {@code @NotBlank}가 애플리케이션 기동 시점에
 * 즉시 실패(fail-fast)하게 한다. secret이 서명 키로 쓰기에 너무 짧은 경우까지는 여기서 검증하지
 * 않는다 — JwtTokenProvider 생성자의 {@code Keys.hmacShaKeyFor()}가 그 즉시(=기동 시점, 싱글턴
 * 빈 생성 시) WeakKeyException으로 이미 fail-fast 처리한다.
 */
@ConfigurationProperties(prefix = "homesense.security.jwt")
@Validated
public record JwtProperties(@NotBlank String secret, long accessTokenValidity, long refreshTokenValidity) {
}
