package com.jiseong.homesense.common.security;

import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Date;

import javax.crypto.SecretKey;

import org.springframework.stereotype.Component;

import com.jiseong.homesense.common.config.JwtProperties;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;

/**
 * COM-SEC-02. Access/Refresh Token의 생성·검증·파싱을 담당한다.
 * Access Token은 role 클레임을 포함해 JwtAuthenticationFilter가 별도 조회 없이 SecurityContext를
 * 채울 수 있게 하고, Refresh Token은 재발급 시점에 DB에서 현재 role을 다시 조회해 쓰도록 role을
 * 담지 않는다(권한이 그 사이 바뀌어도 이전 role이 그대로 재발급되는 것을 막기 위함).
 */
@Component
public class JwtTokenProvider {

    private static final String CLAIM_ROLE = "role";

    private final SecretKey secretKey;
    private final long accessTokenValidity;
    private final long refreshTokenValidity;

    public JwtTokenProvider(JwtProperties jwtProperties) {
        this.secretKey = Keys.hmacShaKeyFor(jwtProperties.secret().getBytes(StandardCharsets.UTF_8));
        this.accessTokenValidity = jwtProperties.accessTokenValidity();
        this.refreshTokenValidity = jwtProperties.refreshTokenValidity();
    }

    public String createAccessToken(Long userId, String role) {
        return buildToken(userId, accessTokenValidity, role);
    }

    public String createRefreshToken(Long userId) {
        return buildToken(userId, refreshTokenValidity, null);
    }

    /**
     * 서명 위조·만료·형식 오류를 모두 false로 통일 처리해 호출부(JwtAuthenticationFilter)가
     * 단일 분기로 처리할 수 있게 한다.
     */
    public boolean validateToken(String token) {
        try {
            parseClaims(token);
            return true;
        } catch (JwtException | IllegalArgumentException e) {
            return false;
        }
    }

    public Long getUserId(String token) {
        return Long.valueOf(parseClaims(token).getSubject());
    }

    public String getRole(String token) {
        return parseClaims(token).get(CLAIM_ROLE, String.class);
    }

    private String buildToken(Long userId, long validityMillis, String role) {
        Instant now = Instant.now();
        var builder = Jwts.builder()
                .subject(String.valueOf(userId))
                .issuedAt(Date.from(now))
                .expiration(Date.from(now.plusMillis(validityMillis)))
                .signWith(secretKey);
        if (role != null) {
            builder.claim(CLAIM_ROLE, role);
        }
        return builder.compact();
    }

    private Claims parseClaims(String token) {
        return Jwts.parser()
                .verifyWith(secretKey)
                .build()
                .parseSignedClaims(token)
                .getPayload();
    }
}
