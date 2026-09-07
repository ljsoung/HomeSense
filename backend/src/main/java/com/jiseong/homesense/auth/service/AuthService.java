package com.jiseong.homesense.auth.service;

import java.time.Duration;
import java.time.LocalDateTime;

import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.jiseong.homesense.auth.dto.LoginCommand;
import com.jiseong.homesense.auth.dto.LoginResponse;
import com.jiseong.homesense.auth.dto.SignupCommand;
import com.jiseong.homesense.auth.dto.SignupResponse;
import com.jiseong.homesense.auth.dto.TokenResponse;
import com.jiseong.homesense.auth.entity.RefreshToken;
import com.jiseong.homesense.auth.exception.AccountLockedException;
import com.jiseong.homesense.auth.exception.AccountNotActiveException;
import com.jiseong.homesense.auth.exception.DuplicateEmailException;
import com.jiseong.homesense.auth.exception.InvalidRefreshTokenException;
import com.jiseong.homesense.auth.repository.RefreshTokenRepository;
import com.jiseong.homesense.common.config.JwtProperties;
import com.jiseong.homesense.common.exception.InvalidCredentialsException;
import com.jiseong.homesense.common.security.JwtTokenProvider;
import com.jiseong.homesense.user.entity.User;
import com.jiseong.homesense.user.entity.UserStatus;
import com.jiseong.homesense.user.repository.UserRepository;

import lombok.RequiredArgsConstructor;

/**
 * SVC-AUTH-01. 회원가입/로그인/토큰 재발급/로그아웃/이메일 중복확인을 담당한다.
 * 토큰 생성·검증 자체는 COM-SEC-02({@link JwtTokenProvider})에 위임하고, 여기서는 자격 증명 검증과
 * Refresh Token의 DB 상태(해시 저장, revoked_yn) 관리만 맡는다.
 */
@Service
@RequiredArgsConstructor
@Transactional
public class AuthService {

    private final UserRepository userRepository;
    private final RefreshTokenRepository refreshTokenRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtTokenProvider jwtTokenProvider;
    private final JwtProperties jwtProperties;
    private final RefreshTokenHasher refreshTokenHasher;
    private final LoginAttemptService loginAttemptService;

    public SignupResponse signup(SignupCommand cmd) {
        if (userRepository.existsByEmail(cmd.email())) {
            throw new DuplicateEmailException();
        }

        User user = User.createUser(cmd.email(), passwordEncoder.encode(cmd.password()), cmd.nickname());
        try {
            userRepository.save(user);
        } catch (DataIntegrityViolationException raceCondition) {
            // email UNIQUE 충돌: existsByEmail() 조회 이후 이 요청이 INSERT하기 전에 같은 이메일로
            // 동시에 들어온 다른 요청이 먼저 삽입을 끝낸 race condition이다(user_id가 IDENTITY라
            // save()가 즉시 INSERT를 실행하므로 이 지점에서 곧바로 터진다). TradeChunkLoader의 dedup_hash
            // race와 달리 여기서는 같은 트랜잭션에서 더 할 일이 없으므로(성공 시 이어지는 토큰 발급을
            // 건너뛰고 그대로 예외를 던져 트랜잭션을 롤백) REQUIRES_NEW 격리 없이 이대로 번역만 하면
            // 된다 — DuplicateEmailException은 GlobalExceptionHandler가 409로 바꿔 원래 existsByEmail()이
            // 잡았어야 할 경우와 동일한 응답을 보장한다(코드리뷰에서 지적된 결함).
            throw new DuplicateEmailException();
        }

        IssuedTokens tokens = loginInternal(user);
        return new SignupResponse(tokens.accessToken(), tokens.refreshToken(), tokens.expiresIn(),
                user.getUserId(), user.getEmail(), user.getNickname());
    }

    public LoginResponse login(LoginCommand cmd) {
        String normalizedEmail = User.normalizeEmail(cmd.email());

        if (loginAttemptService.isLocked(normalizedEmail)) {
            throw new AccountLockedException();
        }

        User user = userRepository.findByEmail(cmd.email()).orElseThrow(InvalidCredentialsException::new);

        if (user.getStatus() != UserStatus.ACTIVE) {
            throw new AccountNotActiveException();
        }

        if (!passwordEncoder.matches(cmd.password(), user.getPassword())) {
            loginAttemptService.recordFailure(normalizedEmail);
            throw new InvalidCredentialsException();
        }

        loginAttemptService.reset(normalizedEmail);

        IssuedTokens tokens = loginInternal(user);
        return new LoginResponse(tokens.accessToken(), tokens.refreshToken(), tokens.expiresIn());
    }

    /**
     * status를 여기서도 확인한다 — JwtAuthenticationFilter는 Access Token의 클레임만 검증할 뿐 매 요청마다
     * 계정 상태를 다시 조회하지 않으므로, 로그인 이후 탈퇴·정지된 계정이라도 이 검사가 없으면 Refresh
     * Token이 만료될 때까지(최대 refreshTokenValidity) 계속 새 Access Token을 발급받을 수 있다 —
     * login()과 동일한 상태 검사를 재발급 경로에도 강제해 막는다(코드리뷰에서 지적된 결함).
     */
    public TokenResponse refreshAccessToken(String refreshTokenValue) {
        if (!jwtTokenProvider.validateToken(refreshTokenValue) || jwtTokenProvider.isAccessToken(refreshTokenValue)) {
            throw new InvalidRefreshTokenException();
        }

        RefreshToken stored = refreshTokenRepository.findByTokenValue(refreshTokenHasher.hash(refreshTokenValue))
                .orElseThrow(InvalidRefreshTokenException::new);
        if (!stored.isUsable()) {
            throw new InvalidRefreshTokenException();
        }

        User user = stored.getUser();
        if (user.getStatus() != UserStatus.ACTIVE) {
            throw new AccountNotActiveException();
        }

        String accessToken = jwtTokenProvider.createAccessToken(user.getUserId(), user.getRole().name());
        return new TokenResponse(accessToken, accessTokenExpiresInSeconds());
    }

    public void logout(Long userId, String refreshTokenValue) {
        RefreshToken stored = refreshTokenRepository.findByTokenValue(refreshTokenHasher.hash(refreshTokenValue))
                .orElseThrow(InvalidRefreshTokenException::new);
        if (!stored.getUser().getUserId().equals(userId)) {
            throw new InvalidRefreshTokenException();
        }
        stored.revoke();
    }

    @Transactional(readOnly = true)
    public boolean isEmailDuplicate(String email) {
        return userRepository.existsByEmail(email);
    }

    /**
     * 설계서 Service 설계표가 명시한 loginInternal() — login()의 토큰 발급 로직 본체다. signup()이
     * 방금 생성한 계정으로 이 메서드를 그대로 재사용해 자동 로그인을 구현하고, login()은 자격 증명
     * 검증(잠금·존재·상태·비밀번호 확인)을 마친 뒤 이 메서드로 토큰 발급만 위임한다.
     */
    private IssuedTokens loginInternal(User user) {
        String accessToken = jwtTokenProvider.createAccessToken(user.getUserId(), user.getRole().name());
        String refreshToken = jwtTokenProvider.createRefreshToken(user.getUserId());

        LocalDateTime expiresAt = LocalDateTime.now().plus(Duration.ofMillis(jwtProperties.refreshTokenValidity()));
        refreshTokenRepository.save(RefreshToken.issue(user, refreshTokenHasher.hash(refreshToken), expiresAt));

        return new IssuedTokens(accessToken, refreshToken, accessTokenExpiresInSeconds());
    }

    private long accessTokenExpiresInSeconds() {
        return jwtProperties.accessTokenValidity() / 1000;
    }

    private record IssuedTokens(String accessToken, String refreshToken, long expiresIn) {
    }
}
