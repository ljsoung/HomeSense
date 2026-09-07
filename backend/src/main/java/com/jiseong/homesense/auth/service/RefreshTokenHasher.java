package com.jiseong.homesense.auth.service;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;

import org.springframework.stereotype.Component;

/**
 * Refresh Token 원문을 DB(refresh_token.token_value)에 저장/조회하기 전에 SHA-256으로 해시한다.
 * JWT 원문을 그대로 저장하면 DB가 유출됐을 때 그 자체로 유효한 Refresh Token이 노출되므로, 조회는
 * 항상 같은 해시로 재계산해 매칭한다(BCrypt와 달리 매 요청마다 원문과 대조해야 하는 값이 아니라
 * 결정적 해시로 충분하다).
 */
@Component
class RefreshTokenHasher {

    private static final String SHA_256 = "SHA-256";

    String hash(String rawToken) {
        try {
            MessageDigest digest = MessageDigest.getInstance(SHA_256);
            byte[] hashed = digest.digest(rawToken.getBytes(StandardCharsets.UTF_8));
            StringBuilder hex = new StringBuilder(hashed.length * 2);
            for (byte b : hashed) {
                hex.append(String.format("%02x", b));
            }
            return hex.toString();
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 알고리즘을 사용할 수 없다", e);
        }
    }
}
