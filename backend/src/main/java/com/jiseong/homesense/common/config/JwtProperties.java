package com.jiseong.homesense.common.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * COM-SEC-02. homesense.security.jwt.* 설정을 바인딩한다.
 * secret은 JWT_SECRET 환경변수(.env)에서 오며 코드에는 절대 하드코딩하지 않는다(NFR-4).
 * accessTokenValidity/refreshTokenValidity 단위는 밀리초다.
 */
@ConfigurationProperties(prefix = "homesense.security.jwt")
public record JwtProperties(String secret, long accessTokenValidity, long refreshTokenValidity) {
}
