package com.jiseong.homesense.auth.service;

import java.time.Duration;
import java.time.Instant;
import java.time.LocalDateTime;

import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import com.jiseong.homesense.auth.dto.LoginCommand;
import com.jiseong.homesense.auth.dto.LoginResponse;
import com.jiseong.homesense.auth.dto.ReactivateCommand;
import com.jiseong.homesense.auth.dto.SignupCommand;
import com.jiseong.homesense.auth.dto.SignupResponse;
import com.jiseong.homesense.auth.dto.TokenResponse;
import com.jiseong.homesense.auth.entity.RefreshToken;
import com.jiseong.homesense.auth.exception.AccountLockedException;
import com.jiseong.homesense.auth.exception.AccountNotActiveException;
import com.jiseong.homesense.auth.exception.AccountNotWithdrawnException;
import com.jiseong.homesense.auth.exception.DuplicateEmailException;
import com.jiseong.homesense.auth.exception.InvalidRefreshTokenException;
import com.jiseong.homesense.auth.exception.InvalidResetTokenException;
import com.jiseong.homesense.auth.exception.PasswordResetCooldownException;
import com.jiseong.homesense.auth.exception.ReactivationPeriodExpiredException;
import com.jiseong.homesense.auth.repository.RefreshTokenRepository;
import com.jiseong.homesense.common.config.JwtProperties;
import com.jiseong.homesense.common.exception.InvalidCredentialsException;
import com.jiseong.homesense.common.security.AccessTokenEpochService;
import com.jiseong.homesense.common.security.JwtTokenProvider;
import com.jiseong.homesense.user.entity.User;
import com.jiseong.homesense.user.entity.UserStatus;
import com.jiseong.homesense.user.repository.UserRepository;
import com.jiseong.homesense.user.service.WithdrawalPolicy;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * SVC-AUTH-01. 회원가입/로그인/탈퇴 철회/토큰 재발급/로그아웃/이메일 중복확인을 담당한다.
 * 토큰 생성·검증 자체는 COM-SEC-02({@link JwtTokenProvider})에 위임하고, 여기서는 자격 증명 검증과
 * Refresh Token의 DB 상태(해시 저장, revoked_yn) 관리만 맡는다.
 */
@Slf4j
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
    private final WithdrawalPolicy withdrawalPolicy;
    private final RefreshTokenRotator refreshTokenRotator;
    private final RefreshTokenReuseHandler refreshTokenReuseHandler;
    private final PasswordResetTokenService passwordResetTokenService;
    private final PasswordResetNotifier passwordResetNotifier;
    private final AccessTokenEpochService accessTokenEpochService;

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

    /**
     * 비밀번호 검증 → status 검사 순서다. 상태를 먼저 보면 비밀번호를 모르는 사람에게도 "탈퇴/정지된 계정"임이
     * 드러나 "계정 존재 여부 비노출" 원칙(AUTH-01)과 어긋난다 — 비밀번호가 틀리면 상태와 무관하게
     * {@link InvalidCredentialsException}이다(설계서 3.1절과 다른 이탈, CLAUDE.md 결정 기록 참고).
     */
    public LoginResponse login(LoginCommand cmd) {
        User user = authenticate(cmd.email(), cmd.password());

        if (user.getStatus() != UserStatus.ACTIVE) {
            throw new AccountNotActiveException(user.getStatus());
        }

        IssuedTokens tokens = loginInternal(user);
        return new LoginResponse(tokens.accessToken(), tokens.refreshToken(), tokens.expiresIn());
    }

    /**
     * 탈퇴 철회. 탈퇴한 계정은 로그인할 수 없어 인증 전 요청이므로 email+password로 본인을 확인하고(로그인과 같은
     * 잠금·실패 카운트 규칙), 유예기간 안이면 ACTIVE로 되돌린 뒤 자동 로그인한다(signup과 같은 패턴).
     *
     * <p>상태 분기는 비밀번호를 아는 사람에게만 보인다: ACTIVE → 409, SUSPENDED → 403(정지 계정은 절대 자가 복구
     * 불가), WITHDRAWN → 계속. 마지막은 조건부 UPDATE의 affected rows로 판정한다 — 0이면 유예기간이 지났거나 그 사이
     * 자동 파기(BAT-USR-01)가 먼저 처리한 것이라 410이다(분산락 없이 DB가 경합을 직렬화한다). 탈퇴 시 폐기된
     * refresh_token은 되살리지 않고 새 토큰을 발급한다.
     */
    public LoginResponse reactivate(ReactivateCommand cmd) {
        User user = authenticate(cmd.email(), cmd.password());

        switch (user.getStatus()) {
            case ACTIVE -> throw new AccountNotWithdrawnException();
            case SUSPENDED -> throw new AccountNotActiveException(UserStatus.SUSPENDED);
            case WITHDRAWN -> {
                // 아래 조건부 UPDATE로 진행
            }
        }

        LocalDateTime now = withdrawalPolicy.now();
        LocalDateTime threshold = withdrawalPolicy.graceThreshold(now);
        if (userRepository.reactivateIfWithinGrace(user.getUserId(), threshold, now) == 0) {
            throw new ReactivationPeriodExpiredException();
        }

        // 벌크 UPDATE가 영속성 컨텍스트를 비웠으므로(clearAutomatically) 갱신된 행을 다시 읽는다.
        User reactivated = userRepository.findById(user.getUserId()).orElseThrow(ReactivationPeriodExpiredException::new);
        IssuedTokens tokens = loginInternal(reactivated);

        log.atInfo()
                .addKeyValue("auditEvent", "ACCOUNT_REACTIVATED")
                .addKeyValue("userId", reactivated.getUserId())
                .log("ACCOUNT_REACTIVATED userId={}", reactivated.getUserId());
        return new LoginResponse(tokens.accessToken(), tokens.refreshToken(), tokens.expiresIn());
    }

    /**
     * login()·reactivate()가 공유하는 자격 증명 검증: 잠금 확인 → 정규화한 이메일로 조회 → BCrypt 매칭 →
     * 실패 카운트 증가/초기화. 존재하지 않는 이메일과 비밀번호 불일치는 같은 {@link InvalidCredentialsException}
     * (동일 문구)이라 둘을 구분할 수 없다. 계정 status는 여기서 보지 않는다 — 호출자가 비밀번호 검증 이후에
     * 판단한다.
     */
    private User authenticate(String rawEmail, String password) {
        String normalizedEmail = User.normalizeEmail(rawEmail);

        if (loginAttemptService.isLocked(normalizedEmail)) {
            throw new AccountLockedException();
        }

        User user = userRepository.findByEmail(rawEmail).orElseThrow(InvalidCredentialsException::new);

        if (!passwordEncoder.matches(password, user.getPassword())) {
            loginAttemptService.recordFailure(normalizedEmail);
            throw new InvalidCredentialsException();
        }

        loginAttemptService.reset(normalizedEmail);
        return user;
    }

    /**
     * 검증+회전 시도 자체는 {@link RefreshTokenRotator#attempt}(자기완결 트랜잭션)에 전부 위임하고,
     * 이 메서드는 그 결과만 보고 분기하는 얇은 오케스트레이터다. {@code NOT_SUPPORTED}로 이 메서드
     * 자신은 트랜잭션을 열지 않는다 — 재사용 탐지 시 뒤이어 부르는 {@link RefreshTokenReuseHandler}가
     * 독립된 새 트랜잭션에서 안전하게 락을 잡으려면, 그 호출 시점에 이 메서드(또는 그 위 어디에도)
     * 열린 트랜잭션이 전혀 없어야 한다 — 자세한 이유는 {@link RefreshTokenReuseHandler}의 javadoc
     * 참고(자기 교착을 실제 동시성 IT로 재현하고서야 확정한 설계다).
     */
    @Transactional(propagation = Propagation.NOT_SUPPORTED)
    public TokenResponse refreshAccessToken(String refreshTokenValue) {
        return switch (refreshTokenRotator.attempt(refreshTokenValue)) {
            case RefreshRotationResult.Rotated(TokenResponse tokenResponse) -> tokenResponse;
            case RefreshRotationResult.ReuseDetected(Long userId) -> {
                refreshTokenReuseHandler.handle(userId);
                throw new InvalidRefreshTokenException();
            }
        };
    }

    /**
     * 제출된 토큰이 이미 rotation으로 교체된 상태({@code stored.isRotated()})라면 그냥 조용히
     * 성공(no-op)시키지 않는다 — 이는 "내가 모르는 사이 이 토큰으로 재발급이 이미 성공해 후속
     * 토큰이 존재한다"는 뜻이라, 탈취된 사본이 그 사이 사용됐을 강한 신호다(코드리뷰 P1 지적: 공격자가
     * 훔친 토큰으로 먼저 회전해 새 토큰을 쥔 뒤, 정상 사용자가 나중에 원래 토큰으로 로그아웃을 호출하면
     * 이 메서드가 "이미 폐기된 토큰을 다시 폐기"하는 것으로만 보여 그 후속 토큰(공격자 세션)을 전혀
     * 건드리지 않은 채 성공을 반환하고 있었다). {@link RefreshTokenReuseHandler}로 위임해
     * {@code refreshAccessToken()}의 재사용 탐지와 동일하게 해당 사용자의 Refresh Token을 전부
     * 폐기한다 — 호출자(로그아웃을 시도한 정상 사용자)에게는 여전히 정상 종료(예외 없음)로 보이는데,
     * "로그아웃"이 의도한 결과(내 세션이 끝난다)를 오히려 더 강하게 충족시키기 때문이다(공격자
     * 세션까지 함께 끊긴다). 이 호출은 {@code refreshAccessToken()}의 재사용 탐지와 달리 이 메서드
     * 자신의 트랜잭션 안에서 그대로 REQUIRES_NEW를 불러도 안전하다 — 여기까지 오는 동안 이 메서드가
     * 수행한 건 비잠금 SELECT뿐이라(revokeIfUnrevoked 같은 조건부 UPDATE를 거치지 않는다) 붙잡고 있는
     * 락이 없고, 새 트랜잭션이 필요한 락을 즉시 잡을 수 있다 — RefreshTokenRotator처럼 별도의 자기완결
     * 트랜잭션으로 분리할 필요가 없다(자세한 이유는 RefreshTokenReuseHandler의 javadoc 참고).
     */
    public void logout(Long userId, String refreshTokenValue) {
        RefreshToken stored = refreshTokenRepository.findByTokenValue(refreshTokenHasher.hash(refreshTokenValue))
                .orElseThrow(InvalidRefreshTokenException::new);
        if (!stored.getUser().getUserId().equals(userId)) {
            throw new InvalidRefreshTokenException();
        }
        if (stored.isRotated()) {
            refreshTokenReuseHandler.handle(userId);
            return;
        }
        stored.revoke();
    }

    @Transactional(readOnly = true)
    public boolean isEmailDuplicate(String email) {
        return userRepository.existsByEmail(email);
    }

    /**
     * AUTH-03 1단계 — 재설정 링크 발송 요청. 계정 존재 여부·상태와 무관하게 호출자에게는 항상 동일한
     * 성공(예외 없음)만 보인다 — 실제로 토큰을 발급하고 메일을 보내는 것은 계정이 존재하고
     * {@code ACTIVE}일 때뿐이다(WITHDRAWN/SUSPENDED에는 보내지 않는다 — AUTH-01의 "탈퇴/정지 계정은
     * 로그인 자체를 차단" 원칙과 정합, CLAUDE.md AUTH-03 결정 기록 참고). 재전송 쿨다운(60초)만 429로
     * 예외를 던지는데, 이 쿨다운은 계정 존재 여부와 무관하게 항상 시도되므로({@link #passwordResetTokenService}
     * 호출이 이 필터 앞에 있다) 오라클이 되지 않는다({@link PasswordResetCooldownException} javadoc
     * 참고).
     *
     * <p>쿨다운 획득은 {@link PasswordResetTokenService#tryStartCooldown}(SETNX) 한 번으로 원자적으로
     * 처리한다 — 이전엔 조회(isCoolingDown)와 세팅(startCooldown)이 별개 호출이라, 같은 이메일로 거의
     * 동시에 여러 요청이 들어오면 전부 "쿨다운 없음"을 관측해 60초 제한을 무시하고 나란히 통과할 수
     * 있었다(P2 코드리뷰 지적) — 그 사이 각자 비동기 SES 발송+토큰 발급을 중복 실행해, 광고된 "60초에
     * 한 번"과 달리 짧은 시간에 여러 통의 메일과 여러 개의 유효한 재설정 토큰이 발급될 수 있었다.
     *
     * <p>토큰 발급+메일 발송은 {@link PasswordResetNotifier#notifyAsync}로 위임해 비동기 실행한다 —
     * 이 메서드 자신은 findByEmail() 하나만 수행하는 읽기 전용 트랜잭션이라 readOnly로 열고, 느린
     * SES 호출이 이 트랜잭션이나 응답 경로를 블로킹하지 않게 한다(그 이유는 PasswordResetNotifier
     * javadoc 참고 — NFR + 타이밍 사이드채널 방지 두 가지 모두 비동기 분리를 요구한다).
     */
    @Transactional(readOnly = true)
    public void requestPasswordReset(String rawEmail) {
        String normalizedEmail = User.normalizeEmail(rawEmail);
        if (!passwordResetTokenService.tryStartCooldown(normalizedEmail)) {
            throw new PasswordResetCooldownException();
        }

        userRepository.findByEmail(rawEmail)
                .filter(user -> user.getStatus() == UserStatus.ACTIVE)
                .ifPresent(user -> passwordResetNotifier.notifyAsync(user.getUserId(), user.getEmail()));
    }

    /**
     * AUTH-03 2단계 진입 전 사전 검증(선택 API — {@code GET /api/auth/password-reset/validate-token}).
     * 만료된 토큰으로 곧바로 입력 폼을 보여주지 않기 위한 것이라 토큰을 소비하지 않는다(peek) — 실제
     * 소비(1회용 삭제)는 {@link #resetPassword}만 한다.
     */
    @Transactional(readOnly = true)
    public void validatePasswordResetToken(String rawToken) {
        if (passwordResetTokenService.peekToken(rawToken).isEmpty()) {
            throw new InvalidResetTokenException();
        }
    }

    /**
     * AUTH-03 2단계 — 토큰을 원자적으로 소비(GETDEL, 1회용)하고 비밀번호를 변경한 뒤 기존
     * Refresh Token을 전부 폐기한다(탈퇴 시 전체 폐기와 동일한 패턴 — 비밀번호가 탈취됐을 가능성에
     * 대비해 재설정 이후에는 모든 기존 세션을 끝낸다, CLAUDE.md AUTH-03 결정 기록 참고).
     *
     * <p>토큰 발급 이후(최대 30분 창) 계정이 탈퇴·정지됐다면 여기서도 {@link InvalidResetTokenException}으로
     * 거부한다 — 그렇지 않으면 WITHDRAWN 계정이 {@link #reactivate}의 비밀번호 확인(authenticate())을
     * 거치지 않고 비밀번호를 바꿀 수 있는 구멍이 된다. 비밀번호 변경(user 엔티티 dirty) 다음에
     * revokeAllByUserId()(벌크 UPDATE)를 호출하는 순서는 {@link RefreshTokenRepository#revokeAllByUserId}의
     * {@code flushAutomatically=true}가 안전하게 처리한다(UserService.withdraw()와 동일한 순서·근거).
     *
     * <p><b>Refresh Token 폐기만으로는 계정 탈취 시나리오를 완전히 막지 못한다(코드리뷰 P1 지적).</b>
     * {@code JwtAuthenticationFilter}는 계정 상태가 ACTIVE이고 서명·만료가 유효하면 이미 발급된
     * Access Token을 그대로 인증에 쓴다 — 비밀번호 재설정은 계정 상태(status)를 바꾸지 않으므로
     * (재설정 후에도 여전히 ACTIVE), 공격자가 재설정 이전에 이미 Access Token을 쥐고 있었다면 그
     * 토큰이 자연 만료될 때까지(최대 {@code accessTokenValidity}, 기본 30분) 재설정 이후에도 계속
     * 인증된 요청을 보낼 수 있었다 — 정확히 비밀번호 재설정이 복구하려는 "계정 탈취" 시나리오에서
     * 방어가 뚫려 있었다는 뜻이다. {@link AccessTokenEpochService}에 "지금 이 순간 이전에 발급된
     * Access Token은 전부 무효"라는 컷오프를 남겨, 다음 요청부터는 그 필터가 이 컷오프와 토큰의
     * {@code iat}를 비교해 재설정 이전 토큰을 걸러낸다(예외 없이 SecurityContext 설정만 건너뜀 —
     * 만료·상태불일치 토큰과 같은 패턴).
     */
    public void resetPassword(String rawToken, String newPassword) {
        Long userId = passwordResetTokenService.consumeToken(rawToken).orElseThrow(InvalidResetTokenException::new);
        User user = userRepository.findById(userId).orElseThrow(InvalidResetTokenException::new);
        if (user.getStatus() != UserStatus.ACTIVE) {
            throw new InvalidResetTokenException();
        }

        user.changePassword(passwordEncoder.encode(newPassword));
        refreshTokenRepository.revokeAllByUserId(userId);
        accessTokenEpochService.invalidateTokensIssuedBefore(userId, Instant.now());

        log.atInfo()
                .addKeyValue("auditEvent", "PASSWORD_RESET_COMPLETED")
                .addKeyValue("userId", userId)
                .log("PASSWORD_RESET_COMPLETED userId={}", userId);
    }

    /**
     * 설계서 Service 설계표가 명시한 loginInternal() — login()의 토큰 발급 로직 본체다. signup()이
     * 방금 생성한 계정으로 이 메서드를 그대로 재사용해 자동 로그인을 구현하고, login()은 자격 증명
     * 검증(잠금·존재·상태·비밀번호 확인)을 마친 뒤 이 메서드로 토큰 발급만 위임한다. reactivate()도
     * DB 상태를 ACTIVE로 되돌린 뒤 이 메서드를 재사용해 자동 로그인한다.
     *
     * <p>이 메서드도 {@code user:status} 캐시에 ACTIVE를 쓰지 않는다 — {@link #refreshAccessToken}의
     * javadoc과 같은 이유(코드리뷰 P1 지적)로, 여기서 읽은 {@code user}의 상태가 이 메서드 실행 도중
     * 다른 트랜잭션의 탈퇴 처리에 의해 이미 낡은 값이 됐을 수 있어 무조건 ACTIVE로 덮어쓰면 안 된다.
     * 새로 발급된 토큰으로 오는 다음 인증 요청은 캐시가 비어 있을 것이므로
     * {@link com.jiseong.homesense.common.security.UserStatusResolver}가 그 시점에 DB를 다시 읽어
     * 캐시를 채운다 — "블록은 즉시, 언블록은 다음 확인 때"라는 비대칭이 이 설계의 핵심이다: WITHDRAWN을
     * 먼저 반영해도(과잉 차단) 다음 정상 요청에서 스스로 바로잡히지만, ACTIVE를 먼저 반영하면(과소
     * 차단) 그 캐시 TTL 동안 탈퇴된 계정이 계속 인증된 것처럼 취급되는 보안 구멍이 된다.
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
