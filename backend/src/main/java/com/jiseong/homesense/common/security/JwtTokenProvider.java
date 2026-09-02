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
 *
 * <p>두 토큰 모두 같은 서명 키로 발급되어 validateToken()만으로는 구분되지 않으므로, 토큰 종류를
 * 별도 클레임(type)으로 명시한다 — role 유무로 암묵적으로 구분하면 role 클레임이 없는 Refresh
 * Token이 "role=null"인 유효한 Access Token처럼 취급되어 인증 우회로 이어질 수 있다.
 */
@Component
public class JwtTokenProvider {

    private static final String CLAIM_ROLE = "role";
    private static final String CLAIM_TYPE = "type";
    private static final String TOKEN_TYPE_ACCESS = "ACCESS";
    private static final String TOKEN_TYPE_REFRESH = "REFRESH";

    private final SecretKey secretKey;
    private final long accessTokenValidity;
    private final long refreshTokenValidity;

    public JwtTokenProvider(JwtProperties jwtProperties) {
        this.secretKey = Keys.hmacShaKeyFor(jwtProperties.secret().getBytes(StandardCharsets.UTF_8));
        this.accessTokenValidity = jwtProperties.accessTokenValidity();
        this.refreshTokenValidity = jwtProperties.refreshTokenValidity();
    }

    public String createAccessToken(Long userId, String role) {
        return buildToken(userId, accessTokenValidity, TOKEN_TYPE_ACCESS, role);
    }

    public String createRefreshToken(Long userId) {
        return buildToken(userId, refreshTokenValidity, TOKEN_TYPE_REFRESH, null);
    }

    /**
     * 서명 위조·만료·형식 오류를 모두 false로 통일 처리해 호출부(JwtAuthenticationFilter)가
     * 단일 분기로 처리할 수 있게 한다. 토큰 종류(Access/Refresh)는 검사하지 않는다 — 구조적으로
     * 유효한 토큰인지만 판단하며, Access Token 전용 검사는 isAccessToken()이 담당한다.
     */
    public boolean validateToken(String token) {
        try {
            parseClaims(token);
            return true;
        } catch (JwtException | IllegalArgumentException e) {
            return false;
        }
    }

    /**
     * SecurityContext 인증에 쓸 수 있는 토큰인지(=Access Token인지) 확인한다. Refresh Token은
     * 같은 서명 키로 발급되어 validateToken()을 통과하지만, /api/auth/refresh 재발급 요청 외의
     * 용도로 Authorization 헤더에 실려 인증에 쓰여서는 안 된다(JwtAuthenticationFilter가 호출).
     */
    public boolean isAccessToken(String token) {
        return TOKEN_TYPE_ACCESS.equals(parseClaims(token).get(CLAIM_TYPE, String.class));
    }

    public Long getUserId(String token) {
        return Long.valueOf(parseClaims(token).getSubject());
    }

    public String getRole(String token) {
        return parseClaims(token).get(CLAIM_ROLE, String.class);
    }

    private String buildToken(Long userId, long validityMillis, String type, String role) {
        Instant now = Instant.now();
        var builder = Jwts.builder()
                .subject(String.valueOf(userId))
                .claim(CLAIM_TYPE, type)
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
