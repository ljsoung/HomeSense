package com.jiseong.homesense.user.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Clock;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.Optional;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.crypto.password.PasswordEncoder;

import com.jiseong.homesense.auth.repository.RefreshTokenRepository;
import com.jiseong.homesense.auth.service.UserSessionRevoker;
import com.jiseong.homesense.common.config.WithdrawalProperties;
import com.jiseong.homesense.common.security.UserStatusCacheService;
import com.jiseong.homesense.user.dto.UpdateUserCommand;
import com.jiseong.homesense.user.dto.UserResponse;
import com.jiseong.homesense.user.dto.WithdrawCommand;
import com.jiseong.homesense.user.entity.User;
import com.jiseong.homesense.user.entity.UserStatus;
import com.jiseong.homesense.user.exception.InvalidCurrentPasswordException;
import com.jiseong.homesense.user.exception.PasswordConfirmLockedException;
import com.jiseong.homesense.user.exception.UserNotFoundException;
import com.jiseong.homesense.user.repository.UserRepository;

@ExtendWith(MockitoExtension.class)
class UserServiceTest {

    @Mock
    private UserRepository userRepository;
    @Mock
    private RefreshTokenRepository refreshTokenRepository;
    @Mock
    private PasswordEncoder passwordEncoder;
    @Mock
    private UserStatusCacheService userStatusCacheService;
    @Mock
    private PasswordConfirmAttemptService passwordConfirmAttemptService;
    @Mock
    private UserSessionRevoker userSessionRevoker;

    private static final ZoneId KST = ZoneId.of("Asia/Seoul");
    private static final LocalDateTime NOW = LocalDateTime.of(2026, 9, 21, 12, 0, 0);

    private UserService userService;

    @BeforeEach
    void setUp() {
        Clock fixedClock = Clock.fixed(NOW.atZone(KST).toInstant(), KST);
        WithdrawalPolicy withdrawalPolicy = new WithdrawalPolicy(fixedClock,
                new WithdrawalProperties(7, new WithdrawalProperties.Purge(true, "0 0 5 * * *")));
        userService = new UserService(userRepository, refreshTokenRepository, passwordEncoder, withdrawalPolicy,
                userStatusCacheService, passwordConfirmAttemptService, userSessionRevoker);
    }

    @Test
    void getUser_존재하지_않으면_UserNotFoundException을_던진다() {
        when(userRepository.findById(1L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> userService.getUser(1L)).isInstanceOf(UserNotFoundException.class);
    }

    @Test
    void getUser_존재하면_회원정보를_반환한다() {
        User user = User.createUser("user@test.com", "encoded", "닉네임");
        when(userRepository.findById(1L)).thenReturn(Optional.of(user));

        UserResponse response = userService.getUser(1L);

        assertThat(response.email()).isEqualTo("user@test.com");
        assertThat(response.nickname()).isEqualTo("닉네임");
    }

    @Test
    void updateUser_존재하지_않으면_UserNotFoundException을_던진다() {
        when(userRepository.findById(1L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> userService.updateUser(1L, new UpdateUserCommand("새닉네임", null, null)))
                .isInstanceOf(UserNotFoundException.class);
    }

    @Test
    void updateUser_닉네임만_주어지면_닉네임만_바뀌고_세션은_그대로다() {
        User user = User.createUser("user@test.com", "encoded", "이전닉네임");
        when(userRepository.findById(1L)).thenReturn(Optional.of(user));

        UserResponse response = userService.updateUser(1L, new UpdateUserCommand("새닉네임", null, null));

        assertThat(response.nickname()).isEqualTo("새닉네임");
        assertThat(user.getPassword()).isEqualTo("encoded");
        verify(passwordConfirmAttemptService, never()).tryAcquire(anyLong());
        verify(userSessionRevoker, never()).revokeAllSessions(anyLong());
    }

    @Test
    void updateUser_비밀번호만_바꾸면_재해시해_저장하고_모든_세션을_끝내고_실패_카운트를_지운다() {
        User user = User.createUser("user@test.com", "encoded-old", "닉네임");
        when(userRepository.findById(1L)).thenReturn(Optional.of(user));
        when(passwordConfirmAttemptService.tryAcquire(1L)).thenReturn(true);
        when(passwordEncoder.matches("current1!", "encoded-old")).thenReturn(true);
        when(passwordEncoder.encode("New1234!")).thenReturn("encoded-new");

        UserResponse response = userService.updateUser(1L, new UpdateUserCommand(null, "current1!", "New1234!"));

        assertThat(user.getPassword()).isEqualTo("encoded-new");
        assertThat(response.nickname()).isEqualTo("닉네임");
        verify(userSessionRevoker).revokeAllSessions(1L);
        verify(passwordConfirmAttemptService).reset(1L);
    }

    @Test
    void updateUser_닉네임과_비밀번호를_함께_바꾸면_둘_다_저장하고_세션을_끝낸다() {
        User user = User.createUser("user@test.com", "encoded-old", "이전닉네임");
        when(userRepository.findById(1L)).thenReturn(Optional.of(user));
        when(passwordConfirmAttemptService.tryAcquire(1L)).thenReturn(true);
        when(passwordEncoder.matches("current1!", "encoded-old")).thenReturn(true);
        when(passwordEncoder.encode("New1234!")).thenReturn("encoded-new");

        UserResponse response = userService.updateUser(1L, new UpdateUserCommand("새닉네임", "current1!", "New1234!"));

        assertThat(response.nickname()).isEqualTo("새닉네임");
        assertThat(user.getPassword()).isEqualTo("encoded-new");
        verify(userSessionRevoker).revokeAllSessions(1L);
    }

    @Test
    void updateUser_현재비밀번호가_불일치하면_400예외와_카운트_증가_닉네임도_비밀번호도_바뀌지_않는다() {
        User user = User.createUser("user@test.com", "encoded-old", "이전닉네임");
        when(userRepository.findById(1L)).thenReturn(Optional.of(user));
        when(passwordConfirmAttemptService.tryAcquire(1L)).thenReturn(true);
        when(passwordEncoder.matches("wrong", "encoded-old")).thenReturn(false);

        assertThatThrownBy(() -> userService.updateUser(1L, new UpdateUserCommand("새닉네임", "wrong", "New1234!")))
                .isInstanceOf(InvalidCurrentPasswordException.class);

        assertThat(user.getPassword()).isEqualTo("encoded-old");
        assertThat(user.getNickname()).isEqualTo("이전닉네임");
        // 예약한 시도가 그대로 실패로 남는다 — 지우지 않는다.
        verify(passwordConfirmAttemptService).tryAcquire(1L);
        verify(passwordConfirmAttemptService, never()).reset(anyLong());
        verify(userSessionRevoker, never()).revokeAllSessions(anyLong());
    }

    @Test
    void updateUser_실패_잠금_상태면_비밀번호를_확인하지_않고_429예외를_던진다() {
        User user = User.createUser("user@test.com", "encoded-old", "이전닉네임");
        when(userRepository.findById(1L)).thenReturn(Optional.of(user));
        when(passwordConfirmAttemptService.tryAcquire(1L)).thenReturn(false);

        assertThatThrownBy(() -> userService.updateUser(1L, new UpdateUserCommand("새닉네임", "current1!", "New1234!")))
                .isInstanceOf(PasswordConfirmLockedException.class);

        verify(passwordEncoder, never()).matches(any(), any());
        verify(passwordConfirmAttemptService, never()).reset(anyLong());
        assertThat(user.getNickname()).isEqualTo("이전닉네임");
        assertThat(user.getPassword()).isEqualTo("encoded-old");
    }

    @Test
    void updateUser_새비밀번호는_있는데_현재비밀번호가_없으면_400예외를_던진다() {
        User user = User.createUser("user@test.com", "encoded-old", "닉네임");
        when(userRepository.findById(1L)).thenReturn(Optional.of(user));

        when(passwordConfirmAttemptService.tryAcquire(1L)).thenReturn(true);

        assertThatThrownBy(() -> userService.updateUser(1L, new UpdateUserCommand(null, null, "New1234!")))
                .isInstanceOf(InvalidCurrentPasswordException.class);

        assertThat(user.getPassword()).isEqualTo("encoded-old");
        verify(passwordEncoder, never()).matches(any(), any());
    }

    @Test
    void updateUser_아무것도_없으면_아무것도_바꾸지_않는다() {
        User user = User.createUser("user@test.com", "encoded", "닉네임");
        when(userRepository.findById(1L)).thenReturn(Optional.of(user));

        userService.updateUser(1L, new UpdateUserCommand(null, null, null));

        assertThat(user.getNickname()).isEqualTo("닉네임");
        assertThat(user.getPassword()).isEqualTo("encoded");
        verify(userSessionRevoker, never()).revokeAllSessions(anyLong());
    }

    @Test
    void withdraw_존재하지_않으면_UserNotFoundException을_던진다() {
        when(userRepository.findById(1L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> userService.withdraw(1L, new WithdrawCommand("pw")))
                .isInstanceOf(UserNotFoundException.class);
    }

    @Test
    void withdraw_비밀번호가_불일치하면_400예외와_공용_카운트_증가_탈퇴처리하지_않는다() {
        User user = User.createUser("user@test.com", "encoded", "닉네임");
        when(userRepository.findById(1L)).thenReturn(Optional.of(user));
        when(passwordConfirmAttemptService.tryAcquire(1L)).thenReturn(true);
        when(passwordEncoder.matches("wrong", "encoded")).thenReturn(false);

        assertThatThrownBy(() -> userService.withdraw(1L, new WithdrawCommand("wrong")))
                .isInstanceOf(InvalidCurrentPasswordException.class);

        assertThat(user.getStatus()).isEqualTo(UserStatus.ACTIVE);
        verify(passwordConfirmAttemptService).tryAcquire(1L);
        verify(passwordConfirmAttemptService, never()).reset(anyLong());
        verify(refreshTokenRepository, never()).revokeAllByUserId(anyLong());
        verify(userStatusCacheService, never()).setStatus(anyLong(), any());
    }

    @Test
    void withdraw_공용_실패_잠금_상태면_비밀번호를_확인하지_않고_429예외를_던진다() {
        User user = User.createUser("user@test.com", "encoded", "닉네임");
        when(userRepository.findById(1L)).thenReturn(Optional.of(user));
        when(passwordConfirmAttemptService.tryAcquire(1L)).thenReturn(false);

        assertThatThrownBy(() -> userService.withdraw(1L, new WithdrawCommand("correct")))
                .isInstanceOf(PasswordConfirmLockedException.class);

        verify(passwordEncoder, never()).matches(any(), any());
        assertThat(user.getStatus()).isEqualTo(UserStatus.ACTIVE);
        verify(refreshTokenRepository, never()).revokeAllByUserId(anyLong());
    }

    @Test
    void withdraw_비밀번호가_일치하면_소프트삭제하고_토큰을_일괄_폐기한다() {
        User user = User.createUser("user@test.com", "encoded", "닉네임");
        when(userRepository.findById(1L)).thenReturn(Optional.of(user));
        when(passwordConfirmAttemptService.tryAcquire(1L)).thenReturn(true);
        when(passwordEncoder.matches("correct", "encoded")).thenReturn(true);

        userService.withdraw(1L, new WithdrawCommand("correct"));

        assertThat(user.getStatus()).isEqualTo(UserStatus.WITHDRAWN);
        // 탈퇴 시각은 유예기간 계산과 같은 Clock(KST)에서 나와야 한다 — JVM 기본 타임존이 아니다.
        assertThat(user.getWithdrawnAt()).isEqualTo(NOW);
        verify(refreshTokenRepository).revokeAllByUserId(eq(1L));
        verify(userStatusCacheService).setStatus(1L, UserStatus.WITHDRAWN);
        verify(passwordConfirmAttemptService).reset(1L);
    }
}
