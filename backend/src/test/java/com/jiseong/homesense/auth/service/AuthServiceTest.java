package com.jiseong.homesense.auth.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.Optional;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.util.ReflectionTestUtils;

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
import com.jiseong.homesense.common.config.WithdrawalProperties;
import com.jiseong.homesense.common.exception.InvalidCredentialsException;
import com.jiseong.homesense.common.security.AccessTokenEpochService;
import com.jiseong.homesense.common.security.JwtTokenProvider;
import com.jiseong.homesense.user.entity.User;
import com.jiseong.homesense.user.entity.UserStatus;
import com.jiseong.homesense.user.repository.UserRepository;
import com.jiseong.homesense.user.service.WithdrawalPolicy;

@ExtendWith(MockitoExtension.class)
class AuthServiceTest {

    @Mock
    private UserRepository userRepository;
    @Mock
    private RefreshTokenRepository refreshTokenRepository;
    @Mock
    private PasswordEncoder passwordEncoder;
    @Mock
    private JwtTokenProvider jwtTokenProvider;
    @Mock
    private RefreshTokenHasher refreshTokenHasher;
    @Mock
    private LoginAttemptService loginAttemptService;
    @Mock
    private RefreshTokenRotator refreshTokenRotator;
    @Mock
    private RefreshTokenReuseHandler refreshTokenReuseHandler;
    @Mock
    private PasswordResetTokenService passwordResetTokenService;
    @Mock
    private PasswordResetNotifier passwordResetNotifier;
    @Mock
    private AccessTokenEpochService accessTokenEpochService;

    private static final ZoneId KST = ZoneId.of("Asia/Seoul");
    private static final LocalDateTime NOW = LocalDateTime.of(2026, 9, 21, 12, 0, 0);
    private static final int GRACE_DAYS = 7;

    private AuthService authService;

    @BeforeEach
    void setUp() {
        JwtProperties jwtProperties = new JwtProperties("test-secret-0123456789", 1_800_000L, 1_209_600_000L);
        WithdrawalPolicy withdrawalPolicy = new WithdrawalPolicy(Clock.fixed(NOW.atZone(KST).toInstant(), KST),
                new WithdrawalProperties(GRACE_DAYS, new WithdrawalProperties.Purge(true, "0 0 5 * * *")));
        authService = new AuthService(userRepository, refreshTokenRepository, passwordEncoder,
                jwtTokenProvider, jwtProperties, refreshTokenHasher, loginAttemptService, withdrawalPolicy,
                refreshTokenRotator, refreshTokenReuseHandler, passwordResetTokenService, passwordResetNotifier,
                accessTokenEpochService);
    }

    @Test
    void signup_이메일이_이미_존재하면_DuplicateEmailException을_던진다() {
        when(userRepository.existsByEmail("dup@test.com")).thenReturn(true);

        assertThatThrownBy(() -> authService.signup(new SignupCommand("dup@test.com", "Abcd1234!", "닉네임")))
                .isInstanceOf(DuplicateEmailException.class);

        verify(userRepository, never()).save(any());
    }

    @Test
    void signup_성공하면_계정을_생성하고_토큰과_요약정보를_반환한다() {
        when(userRepository.existsByEmail("new@test.com")).thenReturn(false);
        when(passwordEncoder.encode("Abcd1234!")).thenReturn("encoded-password");
        when(jwtTokenProvider.createAccessToken(any(), eq("USER"))).thenReturn("access-token");
        when(jwtTokenProvider.createRefreshToken(any())).thenReturn("refresh-token");
        when(refreshTokenHasher.hash("refresh-token")).thenReturn("hashed-refresh-token");

        SignupResponse response = authService.signup(new SignupCommand("new@test.com", "Abcd1234!", "닉네임"));

        assertThat(response.accessToken()).isEqualTo("access-token");
        assertThat(response.refreshToken()).isEqualTo("refresh-token");
        assertThat(response.expiresIn()).isEqualTo(1800L);
        assertThat(response.email()).isEqualTo("new@test.com");
        assertThat(response.nickname()).isEqualTo("닉네임");
        verify(userRepository).save(any(User.class));
        verify(refreshTokenRepository).save(any(RefreshToken.class));
    }

    @Test
    void signup_동시_가입_경쟁으로_UNIQUE_제약이_깨지면_DuplicateEmailException으로_변환한다() {
        // existsByEmail() 조회 시점엔 없었지만, 그 사이 다른 요청이 같은 이메일로 먼저 INSERT를 끝낸 경우.
        when(userRepository.existsByEmail("race@test.com")).thenReturn(false);
        when(passwordEncoder.encode("Abcd1234!")).thenReturn("encoded-password");
        when(userRepository.save(any(User.class))).thenThrow(new DataIntegrityViolationException("email UNIQUE"));

        assertThatThrownBy(() -> authService.signup(new SignupCommand("race@test.com", "Abcd1234!", "닉네임")))
                .isInstanceOf(DuplicateEmailException.class);

        verify(refreshTokenRepository, never()).save(any());
        verify(jwtTokenProvider, never()).createAccessToken(any(), anyString());
    }

    @Test
    void login_잠긴_계정이면_UserRepository를_조회하지_않고_AccountLockedException을_던진다() {
        when(loginAttemptService.isLocked("locked@test.com")).thenReturn(true);

        assertThatThrownBy(() -> authService.login(new LoginCommand("locked@test.com", "Abcd1234!")))
                .isInstanceOf(AccountLockedException.class);

        verify(userRepository, never()).findByEmail(anyString());
    }

    @Test
    void login_존재하지_않는_이메일이면_실패카운트를_증가시키지_않고_InvalidCredentialsException을_던진다() {
        when(loginAttemptService.isLocked(anyString())).thenReturn(false);
        when(userRepository.findByEmail("nouser@test.com")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> authService.login(new LoginCommand("nouser@test.com", "Abcd1234!")))
                .isInstanceOf(InvalidCredentialsException.class);

        verify(loginAttemptService, never()).recordFailure(anyString());
    }

    @Test
    void login_비밀번호가_맞고_탈퇴한_계정이면_ACCOUNT_WITHDRAWN으로_거부하고_토큰을_발급하지_않는다() {
        User withdrawnUser = User.createUser("withdrawn@test.com", "encoded", "닉네임");
        withdrawnUser.withdraw(NOW);
        when(loginAttemptService.isLocked(anyString())).thenReturn(false);
        when(userRepository.findByEmail("withdrawn@test.com")).thenReturn(Optional.of(withdrawnUser));
        when(passwordEncoder.matches("Abcd1234!", "encoded")).thenReturn(true);

        assertThatThrownBy(() -> authService.login(new LoginCommand("withdrawn@test.com", "Abcd1234!")))
                .isInstanceOfSatisfying(AccountNotActiveException.class, e -> {
                    assertThat(e.errorCode()).isEqualTo("ACCOUNT_WITHDRAWN");
                    assertThat(e.httpStatus().value()).isEqualTo(403);
                    assertThat(e.getMessage()).isEqualTo("탈퇴 처리된 계정입니다.");
                    // 철회 UI가 아직 없으므로 "철회할 수 있습니다" 같은 허위 안내를 넣지 않는다.
                    assertThat(e.getMessage()).doesNotContain("철회");
                });

        verify(jwtTokenProvider, never()).createAccessToken(any(), anyString());
    }

    @Test
    void login_비밀번호가_틀리면_탈퇴한_계정이어도_InvalidCredentialsException이고_계정_상태가_드러나지_않는다() {
        User withdrawnUser = User.createUser("withdrawn@test.com", "encoded", "닉네임");
        withdrawnUser.withdraw(NOW);
        when(loginAttemptService.isLocked(anyString())).thenReturn(false);
        when(userRepository.findByEmail("withdrawn@test.com")).thenReturn(Optional.of(withdrawnUser));
        when(passwordEncoder.matches("wrong", "encoded")).thenReturn(false);

        assertThatThrownBy(() -> authService.login(new LoginCommand("withdrawn@test.com", "wrong")))
                .isInstanceOf(InvalidCredentialsException.class);

        verify(loginAttemptService).recordFailure("withdrawn@test.com");
    }

    @Test
    void login_비밀번호가_맞고_정지된_계정이면_ACCOUNT_SUSPENDED로_거부한다() {
        User suspendedUser = User.createUser("suspended@test.com", "encoded", "닉네임");
        ReflectionTestUtils.setField(suspendedUser, "status", UserStatus.SUSPENDED);
        when(loginAttemptService.isLocked(anyString())).thenReturn(false);
        when(userRepository.findByEmail("suspended@test.com")).thenReturn(Optional.of(suspendedUser));
        when(passwordEncoder.matches("Abcd1234!", "encoded")).thenReturn(true);

        assertThatThrownBy(() -> authService.login(new LoginCommand("suspended@test.com", "Abcd1234!")))
                .isInstanceOfSatisfying(AccountNotActiveException.class, e -> {
                    assertThat(e.errorCode()).isEqualTo("ACCOUNT_SUSPENDED");
                    assertThat(e.httpStatus().value()).isEqualTo(403);
                    assertThat(e.getMessage()).isEqualTo("탈퇴하거나 정지된 계정입니다");
                });
    }

    @Test
    void login_비밀번호가_틀리면_실패카운트를_증가시키고_InvalidCredentialsException을_던진다() {
        User user = User.createUser("user@test.com", "encoded", "닉네임");
        when(loginAttemptService.isLocked("user@test.com")).thenReturn(false);
        when(userRepository.findByEmail("user@test.com")).thenReturn(Optional.of(user));
        when(passwordEncoder.matches("wrong", "encoded")).thenReturn(false);

        assertThatThrownBy(() -> authService.login(new LoginCommand("user@test.com", "wrong")))
                .isInstanceOf(InvalidCredentialsException.class);

        verify(loginAttemptService).recordFailure("user@test.com");
    }

    @Test
    void login_성공하면_실패카운트를_초기화하고_토큰을_발급한다() {
        User user = User.createUser("user@test.com", "encoded", "닉네임");
        when(loginAttemptService.isLocked("user@test.com")).thenReturn(false);
        when(userRepository.findByEmail("user@test.com")).thenReturn(Optional.of(user));
        when(passwordEncoder.matches("Abcd1234!", "encoded")).thenReturn(true);
        when(jwtTokenProvider.createAccessToken(any(), eq("USER"))).thenReturn("access-token");
        when(jwtTokenProvider.createRefreshToken(any())).thenReturn("refresh-token");
        when(refreshTokenHasher.hash("refresh-token")).thenReturn("hashed-refresh-token");

        var response = authService.login(new LoginCommand("user@test.com", "Abcd1234!"));

        assertThat(response.accessToken()).isEqualTo("access-token");
        verify(loginAttemptService).reset("user@test.com");
    }

    /*
     * refreshAccessToken()은 이제 검증+회전 로직을 RefreshTokenRotator.attempt()에 전부 위임하는 얇은
     * 오케스트레이터다 — malformed/만료/계정 비활성/성공 회전 등 세부 분기는 RefreshTokenRotatorTest가
     * 담당하고, 여기서는 attempt()의 두 결과(Rotated/ReuseDetected)에 대한 이 메서드 자신의 분기만
     * 검증한다.
     */
    @Test
    void refresh_회전에_성공하면_Rotator의_결과를_그대로_반환하고_ReuseHandler를_부르지_않는다() {
        TokenResponse tokenResponse = new TokenResponse("access", "refresh", 1800L);
        when(refreshTokenRotator.attempt("valid")).thenReturn(new RefreshRotationResult.Rotated(tokenResponse));

        TokenResponse response = authService.refreshAccessToken("valid");

        assertThat(response).isEqualTo(tokenResponse);
        verify(refreshTokenReuseHandler, never()).handle(any());
    }

    @Test
    void refresh_재사용이_탐지되면_ReuseHandler를_부른_뒤_InvalidRefreshTokenException을_던진다() {
        when(refreshTokenRotator.attempt("revoked")).thenReturn(new RefreshRotationResult.ReuseDetected(1L));

        assertThatThrownBy(() -> authService.refreshAccessToken("revoked"))
                .isInstanceOf(InvalidRefreshTokenException.class);

        verify(refreshTokenReuseHandler).handle(1L);
    }

    @Test
    void refresh_Rotator가_던진_예외는_그대로_전파된다() {
        when(refreshTokenRotator.attempt("malformed")).thenThrow(new InvalidRefreshTokenException());

        assertThatThrownBy(() -> authService.refreshAccessToken("malformed"))
                .isInstanceOf(InvalidRefreshTokenException.class);

        verify(refreshTokenReuseHandler, never()).handle(any());
    }

    @Test
    void logout_DB에_없는_토큰이면_InvalidRefreshTokenException을_던진다() {
        when(refreshTokenHasher.hash("unknown")).thenReturn("hashed-unknown");
        when(refreshTokenRepository.findByTokenValue("hashed-unknown")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> authService.logout(1L, "unknown"))
                .isInstanceOf(InvalidRefreshTokenException.class);
    }

    @Test
    void logout_본인_소유가_아닌_토큰이면_InvalidRefreshTokenException을_던진다() {
        User owner = mock(User.class);
        when(owner.getUserId()).thenReturn(2L);
        RefreshToken stored = RefreshToken.issue(owner, "hashed-token", LocalDateTime.now().plusDays(1));
        when(refreshTokenHasher.hash("token")).thenReturn("hashed-token");
        when(refreshTokenRepository.findByTokenValue("hashed-token")).thenReturn(Optional.of(stored));

        assertThatThrownBy(() -> authService.logout(1L, "token"))
                .isInstanceOf(InvalidRefreshTokenException.class);
        assertThat(stored.isUsable()).isTrue();
    }

    @Test
    void logout_본인_토큰이면_폐기한다() {
        User owner = mock(User.class);
        when(owner.getUserId()).thenReturn(1L);
        RefreshToken stored = RefreshToken.issue(owner, "hashed-token", LocalDateTime.now().plusDays(1));
        when(refreshTokenHasher.hash("token")).thenReturn("hashed-token");
        when(refreshTokenRepository.findByTokenValue("hashed-token")).thenReturn(Optional.of(stored));

        authService.logout(1L, "token");

        assertThat(stored.isUsable()).isFalse();
        verify(refreshTokenReuseHandler, never()).handle(any());
    }

    /**
     * 코드리뷰 P1 지적 — 공격자가 탈취한 토큰으로 먼저 rotation해 후속 토큰을 쥔 뒤, 정상 사용자가
     * 나중에 원래(이미 rotation된) 토큰으로 로그아웃을 시도하는 시나리오. 예전 코드는 이를 "이미
     * 폐기된 토큰을 다시 폐기"하는 것으로만 보고 조용히 성공 처리해 공격자의 후속 토큰을 전혀
     * 건드리지 않았다.
     */
    @Test
    void logout_이미_rotation된_토큰이면_재사용_탐지로_전체_폐기하고_예외_없이_종료한다() {
        User owner = mock(User.class);
        when(owner.getUserId()).thenReturn(1L);
        RefreshToken stored = RefreshToken.issue(owner, "hashed-token", LocalDateTime.now().plusDays(1));
        ReflectionTestUtils.setField(stored, "rotatedYn", true);
        when(refreshTokenHasher.hash("token")).thenReturn("hashed-token");
        when(refreshTokenRepository.findByTokenValue("hashed-token")).thenReturn(Optional.of(stored));

        authService.logout(1L, "token");

        verify(refreshTokenReuseHandler).handle(1L);
    }

    // --- reactivate (탈퇴 철회) ---

    private User withdrawnUserWithId(long userId) {
        User user = User.createUser("withdrawn@test.com", "encoded", "닉네임");
        ReflectionTestUtils.setField(user, "userId", userId);
        user.withdraw(NOW.minusDays(3));
        return user;
    }

    @Test
    void reactivate_유예기간_안이면_조건부_UPDATE로_복구하고_새_토큰으로_자동_로그인한다() {
        User user = withdrawnUserWithId(1L);
        when(loginAttemptService.isLocked("withdrawn@test.com")).thenReturn(false);
        when(userRepository.findByEmail("withdrawn@test.com")).thenReturn(Optional.of(user));
        when(passwordEncoder.matches("Abcd1234!", "encoded")).thenReturn(true);
        // threshold = now - graceDays, updatedAt = now — 파기와 같은 정책 객체가 계산한 값이어야 한다.
        when(userRepository.reactivateIfWithinGrace(1L, NOW.minusDays(GRACE_DAYS), NOW)).thenReturn(1);
        // 벌크 UPDATE 이후 영속성 컨텍스트가 비워지므로 갱신된 행을 다시 읽는다.
        User reloaded = User.createUser("withdrawn@test.com", "encoded", "닉네임");
        ReflectionTestUtils.setField(reloaded, "userId", 1L);
        when(userRepository.findById(1L)).thenReturn(Optional.of(reloaded));
        when(jwtTokenProvider.createAccessToken(eq(1L), eq("USER"))).thenReturn("access-token");
        when(jwtTokenProvider.createRefreshToken(1L)).thenReturn("refresh-token");
        when(refreshTokenHasher.hash("refresh-token")).thenReturn("hashed-refresh-token");

        LoginResponse response = authService.reactivate(new ReactivateCommand("withdrawn@test.com", "Abcd1234!"));

        assertThat(response.accessToken()).isEqualTo("access-token");
        assertThat(response.refreshToken()).isEqualTo("refresh-token");
        verify(loginAttemptService).reset("withdrawn@test.com");
        verify(refreshTokenRepository).save(any(RefreshToken.class)); // 새 토큰 발급
        // 탈퇴 시 폐기된 기존 refresh_token은 되살리지 않는다 — 기존 토큰을 건드리는 호출이 없다.
        verify(refreshTokenRepository, never()).revokeAllByUserId(anyLong());
    }

    @Test
    void reactivate_비밀번호가_틀리면_실패카운트를_증가시키고_복구를_시도하지_않는다() {
        User user = withdrawnUserWithId(1L);
        when(loginAttemptService.isLocked("withdrawn@test.com")).thenReturn(false);
        when(userRepository.findByEmail("withdrawn@test.com")).thenReturn(Optional.of(user));
        when(passwordEncoder.matches("wrong", "encoded")).thenReturn(false);

        assertThatThrownBy(() -> authService.reactivate(new ReactivateCommand("withdrawn@test.com", "wrong")))
                .isInstanceOf(InvalidCredentialsException.class);

        verify(loginAttemptService).recordFailure("withdrawn@test.com");
        verify(userRepository, never()).reactivateIfWithinGrace(any(), any(), any());
    }

    @Test
    void reactivate_잠긴_계정이면_조회하지_않고_AccountLockedException을_던진다() {
        when(loginAttemptService.isLocked("locked@test.com")).thenReturn(true);

        assertThatThrownBy(() -> authService.reactivate(new ReactivateCommand("locked@test.com", "Abcd1234!")))
                .isInstanceOf(AccountLockedException.class);

        verify(userRepository, never()).findByEmail(anyString());
    }

    @Test
    void reactivate_존재하지_않는_이메일이면_login과_같은_InvalidCredentialsException이고_카운트를_올리지_않는다() {
        when(loginAttemptService.isLocked(anyString())).thenReturn(false);
        when(userRepository.findByEmail("nouser@test.com")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> authService.reactivate(new ReactivateCommand("nouser@test.com", "Abcd1234!")))
                .isInstanceOf(InvalidCredentialsException.class);

        verify(loginAttemptService, never()).recordFailure(anyString());
    }

    @Test
    void reactivate_이미_ACTIVE인_계정이면_AccountNotWithdrawnException을_던진다() {
        User active = User.createUser("user@test.com", "encoded", "닉네임");
        when(loginAttemptService.isLocked(anyString())).thenReturn(false);
        when(userRepository.findByEmail("user@test.com")).thenReturn(Optional.of(active));
        when(passwordEncoder.matches("Abcd1234!", "encoded")).thenReturn(true);

        assertThatThrownBy(() -> authService.reactivate(new ReactivateCommand("user@test.com", "Abcd1234!")))
                .isInstanceOfSatisfying(AccountNotWithdrawnException.class, e -> {
                    assertThat(e.errorCode()).isEqualTo("ACCOUNT_NOT_WITHDRAWN");
                    assertThat(e.httpStatus().value()).isEqualTo(409);
                });

        verify(userRepository, never()).reactivateIfWithinGrace(any(), any(), any());
    }

    @Test
    void reactivate_정지된_계정은_절대_복구하지_않고_ACCOUNT_SUSPENDED로_거부한다() {
        User suspended = User.createUser("suspended@test.com", "encoded", "닉네임");
        ReflectionTestUtils.setField(suspended, "status", UserStatus.SUSPENDED);
        when(loginAttemptService.isLocked(anyString())).thenReturn(false);
        when(userRepository.findByEmail("suspended@test.com")).thenReturn(Optional.of(suspended));
        when(passwordEncoder.matches("Abcd1234!", "encoded")).thenReturn(true);

        assertThatThrownBy(() -> authService.reactivate(new ReactivateCommand("suspended@test.com", "Abcd1234!")))
                .isInstanceOfSatisfying(AccountNotActiveException.class,
                        e -> assertThat(e.errorCode()).isEqualTo("ACCOUNT_SUSPENDED"));

        verify(userRepository, never()).reactivateIfWithinGrace(any(), any(), any());
    }

    @Test
    void reactivate_유예기간이_지나_조건부_UPDATE가_0건이면_410으로_거부하고_토큰을_발급하지_않는다() {
        User user = withdrawnUserWithId(1L);
        when(loginAttemptService.isLocked(anyString())).thenReturn(false);
        when(userRepository.findByEmail("withdrawn@test.com")).thenReturn(Optional.of(user));
        when(passwordEncoder.matches("Abcd1234!", "encoded")).thenReturn(true);
        when(userRepository.reactivateIfWithinGrace(1L, NOW.minusDays(GRACE_DAYS), NOW)).thenReturn(0);

        assertThatThrownBy(() -> authService.reactivate(new ReactivateCommand("withdrawn@test.com", "Abcd1234!")))
                .isInstanceOfSatisfying(ReactivationPeriodExpiredException.class, e -> {
                    assertThat(e.errorCode()).isEqualTo("REACTIVATION_PERIOD_EXPIRED");
                    assertThat(e.httpStatus().value()).isEqualTo(410);
                });

        verify(jwtTokenProvider, never()).createAccessToken(any(), anyString());
        verify(refreshTokenRepository, never()).save(any());
    }

    // --- requestPasswordReset (AUTH-03 1단계) ---

    @Test
    void requestPasswordReset_쿨다운_획득에_실패하면_계정을_조회하지_않고_PasswordResetCooldownException을_던진다() {
        when(passwordResetTokenService.tryStartCooldown("user@test.com")).thenReturn(false);

        assertThatThrownBy(() -> authService.requestPasswordReset("user@test.com"))
                .isInstanceOf(PasswordResetCooldownException.class);

        verify(userRepository, never()).findByEmail(anyString());
    }

    @Test
    void requestPasswordReset_존재하지_않는_이메일이어도_쿨다운_획득을_시도하고_예외_없이_종료한다() {
        when(passwordResetTokenService.tryStartCooldown("nouser@test.com")).thenReturn(true);
        when(userRepository.findByEmail("nouser@test.com")).thenReturn(Optional.empty());

        authService.requestPasswordReset("nouser@test.com");

        // 계정 존재 여부와 무관하게 항상 쿨다운 획득을 시도해야 재전송 응답 차이가 계정 존재를 드러내는
        // 오라클이 되지 않는다(PasswordResetCooldownException javadoc 참고).
        verify(passwordResetTokenService).tryStartCooldown("nouser@test.com");
        verify(passwordResetNotifier, never()).notifyAsync(any(), anyString());
    }

    @Test
    void requestPasswordReset_탈퇴한_계정이면_쿨다운_획득만_시도하고_알림을_보내지_않는다() {
        User withdrawnUser = User.createUser("withdrawn@test.com", "encoded", "닉네임");
        withdrawnUser.withdraw(NOW);
        when(passwordResetTokenService.tryStartCooldown("withdrawn@test.com")).thenReturn(true);
        when(userRepository.findByEmail("withdrawn@test.com")).thenReturn(Optional.of(withdrawnUser));

        authService.requestPasswordReset("withdrawn@test.com");

        verify(passwordResetNotifier, never()).notifyAsync(any(), anyString());
    }

    @Test
    void requestPasswordReset_ACTIVE_계정이면_쿨다운을_획득하고_비동기_알림을_호출한다() {
        User user = User.createUser("user@test.com", "encoded", "닉네임");
        ReflectionTestUtils.setField(user, "userId", 1L);
        when(passwordResetTokenService.tryStartCooldown("user@test.com")).thenReturn(true);
        when(userRepository.findByEmail("user@test.com")).thenReturn(Optional.of(user));

        authService.requestPasswordReset("user@test.com");

        verify(passwordResetNotifier).notifyAsync(1L, "user@test.com");
    }

    // --- validatePasswordResetToken (AUTH-03 2단계 사전 검증) ---

    @Test
    void validatePasswordResetToken_유효한_토큰이면_예외_없이_종료한다() {
        when(passwordResetTokenService.peekToken("valid-token")).thenReturn(Optional.of(1L));

        authService.validatePasswordResetToken("valid-token");

        verify(passwordResetTokenService, never()).consumeToken(anyString());
    }

    @Test
    void validatePasswordResetToken_존재하지_않으면_InvalidResetTokenException을_던진다() {
        when(passwordResetTokenService.peekToken("bad-token")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> authService.validatePasswordResetToken("bad-token"))
                .isInstanceOf(InvalidResetTokenException.class);
    }

    // --- resetPassword (AUTH-03 2단계) ---

    @Test
    void resetPassword_토큰이_유효하지_않으면_사용자를_조회하지_않고_InvalidResetTokenException을_던진다() {
        when(passwordResetTokenService.consumeToken("bad-token")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> authService.resetPassword("bad-token", "NewAbcd1234!"))
                .isInstanceOf(InvalidResetTokenException.class);

        verify(userRepository, never()).findById(any());
    }

    @Test
    void resetPassword_토큰이_가리키는_사용자가_없으면_InvalidResetTokenException을_던진다() {
        when(passwordResetTokenService.consumeToken("token")).thenReturn(Optional.of(999L));
        when(userRepository.findById(999L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> authService.resetPassword("token", "NewAbcd1234!"))
                .isInstanceOf(InvalidResetTokenException.class);

        verify(refreshTokenRepository, never()).revokeAllByUserId(any());
        verify(accessTokenEpochService, never()).invalidateTokensIssuedBefore(any(), any());
        verify(loginAttemptService, never()).reset(anyString());
    }

    @Test
    void resetPassword_계정이_ACTIVE가_아니면_비밀번호를_바꾸지_않고_InvalidResetTokenException을_던진다() {
        User withdrawnUser = User.createUser("withdrawn@test.com", "encoded", "닉네임");
        ReflectionTestUtils.setField(withdrawnUser, "userId", 1L);
        withdrawnUser.withdraw(NOW);
        when(passwordResetTokenService.consumeToken("token")).thenReturn(Optional.of(1L));
        when(userRepository.findById(1L)).thenReturn(Optional.of(withdrawnUser));

        assertThatThrownBy(() -> authService.resetPassword("token", "NewAbcd1234!"))
                .isInstanceOf(InvalidResetTokenException.class);

        assertThat(withdrawnUser.getPassword()).isEqualTo("encoded");
        verify(refreshTokenRepository, never()).revokeAllByUserId(any());
        verify(accessTokenEpochService, never()).invalidateTokensIssuedBefore(any(), any());
        verify(loginAttemptService, never()).reset(anyString());
    }

    @Test
    void resetPassword_성공하면_비밀번호를_변경하고_모든_RefreshToken을_폐기하고_기존_AccessToken도_무효화하고_로그인_잠금도_해제한다() {
        User user = User.createUser("user@test.com", "encoded", "닉네임");
        ReflectionTestUtils.setField(user, "userId", 1L);
        when(passwordResetTokenService.consumeToken("token")).thenReturn(Optional.of(1L));
        when(userRepository.findById(1L)).thenReturn(Optional.of(user));
        when(passwordEncoder.encode("NewAbcd1234!")).thenReturn("new-encoded");

        Instant before = Instant.now();
        authService.resetPassword("token", "NewAbcd1234!");
        Instant after = Instant.now();

        assertThat(user.getPassword()).isEqualTo("new-encoded");
        verify(refreshTokenRepository).revokeAllByUserId(1L);

        ArgumentCaptor<Instant> cutoffCaptor = ArgumentCaptor.forClass(Instant.class);
        verify(accessTokenEpochService).invalidateTokensIssuedBefore(eq(1L), cutoffCaptor.capture());
        assertThat(cutoffCaptor.getValue()).isBetween(before, after);

        // 재설정 전에 5회 실패로 잠겨 있었더라도, 토큰을 원자적으로 소비해 여기까지 도달한 것 자체가
        // 메일함 소유를 증명하므로 login:fail 카운터도 함께 지워야 새 비밀번호로 곧바로 로그인할 수
        // 있다(코드리뷰 P2 지적).
        verify(loginAttemptService).reset("user@test.com");
    }
}
