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

import java.time.LocalDateTime;
import java.util.Optional;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.security.crypto.password.PasswordEncoder;

import com.jiseong.homesense.auth.dto.LoginCommand;
import com.jiseong.homesense.auth.dto.SignupCommand;
import com.jiseong.homesense.auth.dto.SignupResponse;
import com.jiseong.homesense.auth.dto.TokenResponse;
import com.jiseong.homesense.auth.entity.RefreshToken;
import com.jiseong.homesense.auth.exception.AccountLockedException;
import com.jiseong.homesense.auth.exception.AccountNotActiveException;
import com.jiseong.homesense.auth.exception.DuplicateEmailException;
import com.jiseong.homesense.auth.exception.InvalidCredentialsException;
import com.jiseong.homesense.auth.exception.InvalidRefreshTokenException;
import com.jiseong.homesense.auth.repository.RefreshTokenRepository;
import com.jiseong.homesense.common.config.JwtProperties;
import com.jiseong.homesense.common.security.JwtTokenProvider;
import com.jiseong.homesense.user.entity.User;
import com.jiseong.homesense.user.repository.UserRepository;

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

    private AuthService authService;

    @BeforeEach
    void setUp() {
        JwtProperties jwtProperties = new JwtProperties("test-secret-0123456789", 1_800_000L, 1_209_600_000L);
        authService = new AuthService(userRepository, refreshTokenRepository, passwordEncoder,
                jwtTokenProvider, jwtProperties, refreshTokenHasher, loginAttemptService);
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
    void login_탈퇴한_계정이면_AccountNotActiveException을_던진다() {
        User withdrawnUser = User.createUser("withdrawn@test.com", "encoded", "닉네임");
        withdrawnUser.withdraw();
        when(loginAttemptService.isLocked(anyString())).thenReturn(false);
        when(userRepository.findByEmail("withdrawn@test.com")).thenReturn(Optional.of(withdrawnUser));

        assertThatThrownBy(() -> authService.login(new LoginCommand("withdrawn@test.com", "Abcd1234!")))
                .isInstanceOf(AccountNotActiveException.class);
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

    @Test
    void refresh_토큰_형식이_유효하지_않으면_InvalidRefreshTokenException을_던진다() {
        when(jwtTokenProvider.validateToken("malformed")).thenReturn(false);

        assertThatThrownBy(() -> authService.refreshAccessToken("malformed"))
                .isInstanceOf(InvalidRefreshTokenException.class);

        verify(jwtTokenProvider, never()).isAccessToken(anyString());
    }

    @Test
    void refresh_Access_Token이_제출되면_InvalidRefreshTokenException을_던진다() {
        when(jwtTokenProvider.validateToken("access-token")).thenReturn(true);
        when(jwtTokenProvider.isAccessToken("access-token")).thenReturn(true);

        assertThatThrownBy(() -> authService.refreshAccessToken("access-token"))
                .isInstanceOf(InvalidRefreshTokenException.class);

        verify(refreshTokenRepository, never()).findByTokenValue(anyString());
    }

    @Test
    void refresh_DB에_없는_토큰이면_InvalidRefreshTokenException을_던진다() {
        when(jwtTokenProvider.validateToken("unknown")).thenReturn(true);
        when(jwtTokenProvider.isAccessToken("unknown")).thenReturn(false);
        when(refreshTokenHasher.hash("unknown")).thenReturn("hashed-unknown");
        when(refreshTokenRepository.findByTokenValue("hashed-unknown")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> authService.refreshAccessToken("unknown"))
                .isInstanceOf(InvalidRefreshTokenException.class);
    }

    @Test
    void refresh_폐기된_토큰이면_InvalidRefreshTokenException을_던진다() {
        User user = User.createUser("user@test.com", "encoded", "닉네임");
        RefreshToken revoked = RefreshToken.issue(user, "hashed-revoked", LocalDateTime.now().plusDays(1));
        revoked.revoke();
        when(jwtTokenProvider.validateToken("revoked")).thenReturn(true);
        when(jwtTokenProvider.isAccessToken("revoked")).thenReturn(false);
        when(refreshTokenHasher.hash("revoked")).thenReturn("hashed-revoked");
        when(refreshTokenRepository.findByTokenValue("hashed-revoked")).thenReturn(Optional.of(revoked));

        assertThatThrownBy(() -> authService.refreshAccessToken("revoked"))
                .isInstanceOf(InvalidRefreshTokenException.class);
    }

    @Test
    void refresh_만료된_토큰이면_InvalidRefreshTokenException을_던진다() {
        User user = User.createUser("user@test.com", "encoded", "닉네임");
        RefreshToken expired = RefreshToken.issue(user, "hashed-expired", LocalDateTime.now().minusMinutes(1));
        when(jwtTokenProvider.validateToken("expired")).thenReturn(true);
        when(jwtTokenProvider.isAccessToken("expired")).thenReturn(false);
        when(refreshTokenHasher.hash("expired")).thenReturn("hashed-expired");
        when(refreshTokenRepository.findByTokenValue("hashed-expired")).thenReturn(Optional.of(expired));

        assertThatThrownBy(() -> authService.refreshAccessToken("expired"))
                .isInstanceOf(InvalidRefreshTokenException.class);
    }

    @Test
    void refresh_탈퇴하거나_정지된_계정이면_AccountNotActiveException을_던진다() {
        User withdrawnUser = User.createUser("withdrawn@test.com", "encoded", "닉네임");
        withdrawnUser.withdraw();
        RefreshToken stored = RefreshToken.issue(withdrawnUser, "hashed-token", LocalDateTime.now().plusDays(1));
        when(jwtTokenProvider.validateToken("token")).thenReturn(true);
        when(jwtTokenProvider.isAccessToken("token")).thenReturn(false);
        when(refreshTokenHasher.hash("token")).thenReturn("hashed-token");
        when(refreshTokenRepository.findByTokenValue("hashed-token")).thenReturn(Optional.of(stored));

        assertThatThrownBy(() -> authService.refreshAccessToken("token"))
                .isInstanceOf(AccountNotActiveException.class);

        verify(jwtTokenProvider, never()).createAccessToken(any(), anyString());
    }

    @Test
    void refresh_성공하면_새_Access_Token만_발급한다() {
        User user = User.createUser("user@test.com", "encoded", "닉네임");
        RefreshToken valid = RefreshToken.issue(user, "hashed-valid", LocalDateTime.now().plusDays(1));
        when(jwtTokenProvider.validateToken("valid")).thenReturn(true);
        when(jwtTokenProvider.isAccessToken("valid")).thenReturn(false);
        when(refreshTokenHasher.hash("valid")).thenReturn("hashed-valid");
        when(refreshTokenRepository.findByTokenValue("hashed-valid")).thenReturn(Optional.of(valid));
        when(jwtTokenProvider.createAccessToken(any(), eq("USER"))).thenReturn("new-access-token");

        TokenResponse response = authService.refreshAccessToken("valid");

        assertThat(response.accessToken()).isEqualTo("new-access-token");
        assertThat(response.expiresIn()).isEqualTo(1800L);
        verify(jwtTokenProvider, never()).createRefreshToken(anyLong());
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
    }
}
