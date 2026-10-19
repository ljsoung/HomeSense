package com.jiseong.homesense.auth.service;

import java.time.Instant;

import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import com.jiseong.homesense.auth.repository.RefreshTokenRepository;
import com.jiseong.homesense.common.security.AccessTokenEpochService;

/**
 * 한 회원의 모든 세션을 끝낸다 — Refresh Token 전체 폐기 + "지금 이전에 발급된 Access Token은 무효" 컷오프.
 * 비밀번호 재설정(AUTH-03 {@code AuthService.resetPassword})과 비밀번호 변경(SVC-USER-01 {@code UserService.updateUser})이
 * 같은 경로를 쓴다.
 *
 * <p><b>원자성:</b> 호출하는 쪽의 트랜잭션 안에서만 부른다({@link Propagation#MANDATORY}). 비밀번호 저장·Refresh Token
 * 폐기(DB)와 컷오프 쓰기(Redis)가 함께 성공하거나 함께 실패해야 한다 — 컷오프 SET이 예외를 던지면 호출자의 트랜잭션이
 * 롤백돼 비밀번호도 토큰도 그대로 남고 사용자는 오류를 본다(CLAUDE.md "AUTH-03 resetPassword() — Access Token 즉시
 * 무효화" 절의 "resetPassword()의 컷오프 순서" 행). 재사용 탐지({@code RefreshTokenReuseHandler})는 폐기를 먼저
 * 커밋한 뒤 컷오프를 쓰는 다른 순서라 이 컴포넌트를 쓰지 않는다.
 *
 * <p>비밀번호 변경(user 엔티티 dirty) 다음에 부르면 {@link RefreshTokenRepository#revokeAllByUserId}의
 * {@code flushAutomatically=true}가 그 변경을 먼저 flush한다(UserService.withdraw()와 같은 순서·근거).
 */
@Component
public class UserSessionRevoker {

    private final RefreshTokenRepository refreshTokenRepository;
    private final AccessTokenEpochService accessTokenEpochService;

    public UserSessionRevoker(RefreshTokenRepository refreshTokenRepository, AccessTokenEpochService accessTokenEpochService) {
        this.refreshTokenRepository = refreshTokenRepository;
        this.accessTokenEpochService = accessTokenEpochService;
    }

    @Transactional(propagation = Propagation.MANDATORY)
    public void revokeAllSessions(Long userId) {
        refreshTokenRepository.revokeAllByUserId(userId);
        accessTokenEpochService.invalidateTokensIssuedBefore(userId, Instant.now());
    }
}
