package com.jiseong.homesense.auth.service;

import java.time.Duration;
import java.time.LocalDateTime;

import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import com.jiseong.homesense.auth.dto.TokenResponse;
import com.jiseong.homesense.auth.entity.RefreshToken;
import com.jiseong.homesense.auth.exception.AccountNotActiveException;
import com.jiseong.homesense.auth.exception.InvalidRefreshTokenException;
import com.jiseong.homesense.auth.repository.RefreshTokenRepository;
import com.jiseong.homesense.common.config.JwtProperties;
import com.jiseong.homesense.common.security.JwtTokenProvider;
import com.jiseong.homesense.user.entity.User;
import com.jiseong.homesense.user.entity.UserStatus;

import lombok.RequiredArgsConstructor;

/**
 * SVC-AUTH-01.refreshAccessToken() 검증+회전 시도를 자기완결된 트랜잭션 하나로 묶는다. 이 클래스가
 * {@code AuthService.refreshAccessToken()}에서 분리돼 나온 이유는 {@link RefreshTokenReuseHandler}
 * 참고 — 이 메서드의 트랜잭션이 반환 시점에 완전히 커밋(=락 전부 반납)돼야, 그 뒤에 안전하게
 * {@link RefreshTokenReuseHandler}를 호출할 수 있다.
 */
@Component
@RequiredArgsConstructor
class RefreshTokenRotator {

    private final RefreshTokenRepository refreshTokenRepository;
    private final RefreshTokenHasher refreshTokenHasher;
    private final JwtTokenProvider jwtTokenProvider;
    private final JwtProperties jwtProperties;

    /**
     * status를 여기서도 확인한다 — JwtAuthenticationFilter는 Access Token의 클레임만 검증할 뿐 매 요청마다
     * 계정 상태를 다시 조회하지 않으므로, 로그인 이후 탈퇴·정지된 계정이라도 이 검사가 없으면 Refresh
     * Token이 만료될 때까지(최대 refreshTokenValidity) 계속 새 Access Token을 발급받을 수 있다 —
     * login()과 동일한 상태 검사를 재발급 경로에도 강제해 막는다(코드리뷰에서 지적된 결함).
     *
     * <p>회전에 성공해도 {@code user:status} 캐시에 ACTIVE를 쓰지 않는다 — 위에서 읽은
     * {@code user.getStatus()}는 이 메서드가 시작될 때(정확히는 findByTokenValue()가 REPEATABLE
     * READ 스냅샷을 여는 시점)의 값이라, 이 메서드가 실행되는 도중(GC 정지·스레드 스케줄링 지연 등)
     * 다른 트랜잭션이 같은 사용자를 탈퇴시켜 캐시에 WITHDRAWN을 먼저 써 놓았다면, 그 뒤에 이 메서드가
     * 뒤늦게 재개돼 캐시를 ACTIVE로 덮어써 버릴 수 있다(코드리뷰 P1 지적) — 이러면 이 기능 전체의
     * 목적(탈퇴 즉시 차단)이 무력화된다. 캐시는 {@link com.jiseong.homesense.common.security.UserStatusResolver}가
     * 다음 인증 요청에서 캐시미스를 만나는 순간 그 시점의 최신 DB 값을 {@code setIfAbsent}(SETNX)로
     * 안전하게 채운다 — 그 폴백 쓰기도 무조건 덮어쓰기였다면 이 메서드와 같은 종류의 race가 났을
     * 것이므로, "읽기와 쓰기 사이 창이 좁다"는 이유로 단순 SET을 쓰지 않는다(CLAUDE.md 인증 절 참고).
     *
     * <p><b>Refresh Token Rotation + 재사용 탐지.</b> 만료(expiresAt 경과)만 된 토큰은 평범한 재로그인
     * 유도 상황이라 {@link InvalidRefreshTokenException}만 던지고 끝낸다. 반면 이미 {@code revoked_yn=
     * true}인 토큰으로 재발급이 시도되면 — rotation으로 이미 교체됐거나, 로그아웃으로 폐기됐거나,
     * 공격자가 탈취한 사본과 정상 사용자가 사실상 동시에 같은 토큰을 쓴 경우(아래 {@code
     * revokeIfUnrevoked}의 affected rows=0으로 판정) — 토큰 탈취의 강한 신호이므로 예외 대신
     * {@link RefreshRotationResult.ReuseDetected}를 반환해 호출자({@link AuthService#
     * refreshAccessToken})가 이 트랜잭션이 끝난 뒤에 {@link RefreshTokenReuseHandler}로 처리하게
     * 한다 — 왜 이 메서드 안에서 곧바로 처리하지 않는지는 그 클래스의 javadoc 참고(자기 교착 회피).
     *
     * <p>"이미 폐기됨"의 판정 자체를 {@code stored.isRevoked()}로 먼저 읽고 나서 revoke하는 대신,
     * {@code revokeIfUnrevoked()} 조건부 UPDATE 하나로 판정과 폐기를 원자적으로 묶는다 — 같은 토큰으로
     * 거의 동시에 두 번 재발급을 시도하는 경쟁 상황에서, 각자 조회 시점엔 둘 다 유효해 보이지만 이
     * UPDATE는 정확히 하나만 성공시킨다(affected rows). 이 프로젝트가 이미 반복해서 쓴 "조회 후 판단"의
     * TOCTOU를 "조건부 UPDATE + affected rows"로 구조적으로 막는 패턴이다(UserRepository.
     * reactivateIfWithinGrace 등과 동일). 계정이 ACTIVE가 아니면 이 원자적 회전 자체를 시도하지 않는다
     * — 어차피 실패할 요청 때문에 유효한 토큰을 불필요하게 소모시키지 않기 위함이다.
     */
    @Transactional
    RefreshRotationResult attempt(String refreshTokenValue) {
        if (!jwtTokenProvider.validateToken(refreshTokenValue) || jwtTokenProvider.isAccessToken(refreshTokenValue)) {
            throw new InvalidRefreshTokenException();
        }

        RefreshToken stored = refreshTokenRepository.findByTokenValue(refreshTokenHasher.hash(refreshTokenValue))
                .orElseThrow(InvalidRefreshTokenException::new);
        if (stored.isExpired()) {
            throw new InvalidRefreshTokenException();
        }

        User user = stored.getUser();
        if (user.getStatus() != UserStatus.ACTIVE) {
            throw new AccountNotActiveException(user.getStatus());
        }

        if (refreshTokenRepository.revokeIfUnrevoked(stored.getRefreshTokenId()) == 0) {
            return new RefreshRotationResult.ReuseDetected(user.getUserId());
        }

        String accessToken = jwtTokenProvider.createAccessToken(user.getUserId(), user.getRole().name());
        String newRefreshToken = jwtTokenProvider.createRefreshToken(user.getUserId());
        LocalDateTime expiresAt = LocalDateTime.now().plus(Duration.ofMillis(jwtProperties.refreshTokenValidity()));
        refreshTokenRepository.save(RefreshToken.issue(user, refreshTokenHasher.hash(newRefreshToken), expiresAt));

        long expiresIn = jwtProperties.accessTokenValidity() / 1000;
        return new RefreshRotationResult.Rotated(new TokenResponse(accessToken, newRefreshToken, expiresIn));
    }
}
