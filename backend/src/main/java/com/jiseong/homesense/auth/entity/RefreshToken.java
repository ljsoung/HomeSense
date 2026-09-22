package com.jiseong.homesense.auth.entity;

import java.time.LocalDateTime;

import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

import com.jiseong.homesense.user.entity.User;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EntityListeners;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Entity
@Table(name = "refresh_token")
@EntityListeners(AuditingEntityListener.class)
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class RefreshToken {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "refresh_token_id")
    private Long refreshTokenId;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    @Column(name = "token_value", nullable = false, unique = true, length = 255)
    private String tokenValue;

    @Column(name = "expires_at", nullable = false)
    private LocalDateTime expiresAt;

    @Column(name = "revoked_yn", nullable = false)
    private boolean revokedYn;

    /**
     * 이 토큰이 revoked_yn=true가 된 원인이 rotation(=이 토큰으로 재발급이 성공해 후속 토큰이 이미
     * 발급됨)이었는지 구분한다. 로그아웃이나 재사용 탐지의 일괄 폐기로 인한 revoked_yn=true와 구분해야
     * 하는 이유는 {@link com.jiseong.homesense.auth.service.AuthService#logout} 참고 — 도메인
     * 메서드로 세팅하지 않는다(항상 {@code RefreshTokenRepository#revokeIfUnrevoked}의 원자적 UPDATE가
     * revoked_yn과 함께 같은 문장에서 세팅한다, TOCTOU 없이).
     */
    @Column(name = "rotated_yn", nullable = false)
    private boolean rotatedYn;

    @CreatedDate
    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @Builder
    private RefreshToken(User user, String tokenValue, LocalDateTime expiresAt) {
        this.user = user;
        this.tokenValue = tokenValue;
        this.expiresAt = expiresAt;
        this.revokedYn = false;
        this.rotatedYn = false;
    }

    public static RefreshToken issue(User user, String tokenValue, LocalDateTime expiresAt) {
        return RefreshToken.builder()
                .user(user)
                .tokenValue(tokenValue)
                .expiresAt(expiresAt)
                .build();
    }

    public void revoke() {
        this.revokedYn = true;
    }

    public boolean isUsable() {
        return !isRevoked() && !isExpired();
    }

    public boolean isRevoked() {
        return revokedYn;
    }

    public boolean isRotated() {
        return rotatedYn;
    }

    public boolean isExpired() {
        return !expiresAt.isAfter(LocalDateTime.now());
    }
}
