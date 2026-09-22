package com.jiseong.homesense.auth.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
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
import org.springframework.test.util.ReflectionTestUtils;

import com.jiseong.homesense.auth.dto.TokenResponse;
import com.jiseong.homesense.auth.entity.RefreshToken;
import com.jiseong.homesense.auth.exception.AccountNotActiveException;
import com.jiseong.homesense.auth.exception.InvalidRefreshTokenException;
import com.jiseong.homesense.auth.repository.RefreshTokenRepository;
import com.jiseong.homesense.common.config.JwtProperties;
import com.jiseong.homesense.common.security.JwtTokenProvider;
import com.jiseong.homesense.user.entity.User;

@ExtendWith(MockitoExtension.class)
class RefreshTokenRotatorTest {

    @Mock
    private RefreshTokenRepository refreshTokenRepository;
    @Mock
    private JwtTokenProvider jwtTokenProvider;
    @Mock
    private RefreshTokenHasher refreshTokenHasher;

    private RefreshTokenRotator rotator;

    @BeforeEach
    void setUp() {
        JwtProperties jwtProperties = new JwtProperties("test-secret-0123456789", 1_800_000L, 1_209_600_000L);
        rotator = new RefreshTokenRotator(refreshTokenRepository, refreshTokenHasher, jwtTokenProvider, jwtProperties);
    }

    @Test
    void 토큰_형식이_유효하지_않으면_InvalidRefreshTokenException을_던진다() {
        when(jwtTokenProvider.validateToken("malformed")).thenReturn(false);

        assertThatThrownBy(() -> rotator.attempt("malformed"))
                .isInstanceOf(InvalidRefreshTokenException.class);

        verify(jwtTokenProvider, never()).isAccessToken(anyString());
    }

    @Test
    void Access_Token이_제출되면_InvalidRefreshTokenException을_던진다() {
        when(jwtTokenProvider.validateToken("access-token")).thenReturn(true);
        when(jwtTokenProvider.isAccessToken("access-token")).thenReturn(true);

        assertThatThrownBy(() -> rotator.attempt("access-token"))
                .isInstanceOf(InvalidRefreshTokenException.class);

        verify(refreshTokenRepository, never()).findByTokenValue(anyString());
    }

    @Test
    void DB에_없는_토큰이면_InvalidRefreshTokenException을_던진다() {
        when(jwtTokenProvider.validateToken("unknown")).thenReturn(true);
        when(jwtTokenProvider.isAccessToken("unknown")).thenReturn(false);
        when(refreshTokenHasher.hash("unknown")).thenReturn("hashed-unknown");
        when(refreshTokenRepository.findByTokenValue("hashed-unknown")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> rotator.attempt("unknown"))
                .isInstanceOf(InvalidRefreshTokenException.class);
    }

    @Test
    void 만료된_토큰이면_회전을_시도하지_않고_InvalidRefreshTokenException을_던진다() {
        User user = User.createUser("user@test.com", "encoded", "닉네임");
        RefreshToken expired = RefreshToken.issue(user, "hashed-expired", LocalDateTime.now().minusMinutes(1));
        when(jwtTokenProvider.validateToken("expired")).thenReturn(true);
        when(jwtTokenProvider.isAccessToken("expired")).thenReturn(false);
        when(refreshTokenHasher.hash("expired")).thenReturn("hashed-expired");
        when(refreshTokenRepository.findByTokenValue("hashed-expired")).thenReturn(Optional.of(expired));

        assertThatThrownBy(() -> rotator.attempt("expired"))
                .isInstanceOf(InvalidRefreshTokenException.class);

        verify(refreshTokenRepository, never()).revokeIfUnrevoked(any());
    }

    @Test
    void 탈퇴하거나_정지된_계정이면_AccountNotActiveException을_던지고_회전을_시도하지_않는다() {
        User withdrawnUser = User.createUser("withdrawn@test.com", "encoded", "닉네임");
        withdrawnUser.withdraw(LocalDateTime.now());
        RefreshToken stored = RefreshToken.issue(withdrawnUser, "hashed-token", LocalDateTime.now().plusDays(1));
        when(jwtTokenProvider.validateToken("token")).thenReturn(true);
        when(jwtTokenProvider.isAccessToken("token")).thenReturn(false);
        when(refreshTokenHasher.hash("token")).thenReturn("hashed-token");
        when(refreshTokenRepository.findByTokenValue("hashed-token")).thenReturn(Optional.of(stored));

        assertThatThrownBy(() -> rotator.attempt("token"))
                .isInstanceOfSatisfying(AccountNotActiveException.class,
                        e -> assertThat(e.errorCode()).isEqualTo("ACCOUNT_WITHDRAWN"));

        verify(jwtTokenProvider, never()).createAccessToken(any(), anyString());
        verify(refreshTokenRepository, never()).revokeIfUnrevoked(any());
    }

    /**
     * revokeIfUnrevoked()가 0을 반환하는 경우를 대표한다 — "훨씬 전에 로그아웃/rotation으로 이미
     * 폐기된 토큰의 재사용"과 "같은 토큰으로 두 요청이 거의 동시에 재발급을 시도해 이 요청이 경쟁에서
     * 진 경우"를 이 원자적 체크는 구분하지 않는다(구분할 필요도 없다 — 둘 다 같은 신호로 취급한다).
     * 이 메서드는 예외를 직접 던지지 않고 {@link RefreshRotationResult.ReuseDetected}를 반환한다 —
     * 실제 전체 폐기+예외 변환은 {@code AuthService.refreshAccessToken()}이 이 트랜잭션이 끝난 뒤에 한다.
     */
    @Test
    void 이미_폐기된_토큰이면_ReuseDetected_결과를_반환하고_새_토큰을_발급하지_않는다() {
        User user = User.createUser("user@test.com", "encoded", "닉네임");
        ReflectionTestUtils.setField(user, "userId", 1L);
        RefreshToken stored = RefreshToken.issue(user, "hashed-revoked", LocalDateTime.now().plusDays(1));
        ReflectionTestUtils.setField(stored, "refreshTokenId", 10L);
        when(jwtTokenProvider.validateToken("revoked")).thenReturn(true);
        when(jwtTokenProvider.isAccessToken("revoked")).thenReturn(false);
        when(refreshTokenHasher.hash("revoked")).thenReturn("hashed-revoked");
        when(refreshTokenRepository.findByTokenValue("hashed-revoked")).thenReturn(Optional.of(stored));
        when(refreshTokenRepository.revokeIfUnrevoked(10L)).thenReturn(0);

        RefreshRotationResult result = rotator.attempt("revoked");

        assertThat(result).isEqualTo(new RefreshRotationResult.ReuseDetected(1L));
        verify(jwtTokenProvider, never()).createAccessToken(any(), anyString());
        verify(refreshTokenRepository, never()).save(any());
    }

    @Test
    void 성공하면_Access_Refresh_Token_쌍을_모두_발급하고_기존_토큰을_폐기한다() {
        User user = User.createUser("user@test.com", "encoded", "닉네임");
        ReflectionTestUtils.setField(user, "userId", 1L);
        RefreshToken valid = RefreshToken.issue(user, "hashed-valid", LocalDateTime.now().plusDays(1));
        ReflectionTestUtils.setField(valid, "refreshTokenId", 20L);
        when(jwtTokenProvider.validateToken("valid")).thenReturn(true);
        when(jwtTokenProvider.isAccessToken("valid")).thenReturn(false);
        when(refreshTokenHasher.hash("valid")).thenReturn("hashed-valid");
        when(refreshTokenRepository.findByTokenValue("hashed-valid")).thenReturn(Optional.of(valid));
        when(refreshTokenRepository.revokeIfUnrevoked(20L)).thenReturn(1);
        when(jwtTokenProvider.createAccessToken(1L, "USER")).thenReturn("new-access-token");
        when(jwtTokenProvider.createRefreshToken(1L)).thenReturn("new-refresh-token");
        when(refreshTokenHasher.hash("new-refresh-token")).thenReturn("hashed-new-refresh-token");

        RefreshRotationResult result = rotator.attempt("valid");

        assertThat(result).isInstanceOfSatisfying(RefreshRotationResult.Rotated.class, rotated -> {
            TokenResponse response = rotated.tokenResponse();
            assertThat(response.accessToken()).isEqualTo("new-access-token");
            assertThat(response.refreshToken()).isEqualTo("new-refresh-token");
            assertThat(response.expiresIn()).isEqualTo(1800L);
        });
        verify(refreshTokenRepository).save(any(RefreshToken.class));
    }
}
