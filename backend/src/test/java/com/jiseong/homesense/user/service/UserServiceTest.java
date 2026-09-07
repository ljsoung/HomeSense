package com.jiseong.homesense.user.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.Optional;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.crypto.password.PasswordEncoder;

import com.jiseong.homesense.auth.repository.RefreshTokenRepository;
import com.jiseong.homesense.common.exception.InvalidCredentialsException;
import com.jiseong.homesense.user.dto.UpdateUserCommand;
import com.jiseong.homesense.user.dto.UserResponse;
import com.jiseong.homesense.user.dto.WithdrawCommand;
import com.jiseong.homesense.user.entity.User;
import com.jiseong.homesense.user.entity.UserStatus;
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

    private UserService userService;

    @BeforeEach
    void setUp() {
        userService = new UserService(userRepository, refreshTokenRepository, passwordEncoder);
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
    void updateUser_닉네임만_주어지면_닉네임만_바뀐다() {
        User user = User.createUser("user@test.com", "encoded", "이전닉네임");
        when(userRepository.findById(1L)).thenReturn(Optional.of(user));

        UserResponse response = userService.updateUser(1L, new UpdateUserCommand("새닉네임", null, null));

        assertThat(response.nickname()).isEqualTo("새닉네임");
        assertThat(user.getPassword()).isEqualTo("encoded");
    }

    @Test
    void updateUser_현재비밀번호가_일치하면_비밀번호를_재해시하여_저장한다() {
        User user = User.createUser("user@test.com", "encoded-old", "닉네임");
        when(userRepository.findById(1L)).thenReturn(Optional.of(user));
        when(passwordEncoder.matches("current1!", "encoded-old")).thenReturn(true);
        when(passwordEncoder.encode("New1234!")).thenReturn("encoded-new");

        userService.updateUser(1L, new UpdateUserCommand(null, "current1!", "New1234!"));

        assertThat(user.getPassword()).isEqualTo("encoded-new");
    }

    @Test
    void updateUser_현재비밀번호가_불일치하면_InvalidCredentialsException을_던지고_비밀번호를_바꾸지_않는다() {
        User user = User.createUser("user@test.com", "encoded-old", "닉네임");
        when(userRepository.findById(1L)).thenReturn(Optional.of(user));
        when(passwordEncoder.matches("wrong", "encoded-old")).thenReturn(false);

        assertThatThrownBy(() -> userService.updateUser(1L, new UpdateUserCommand(null, "wrong", "New1234!")))
                .isInstanceOf(InvalidCredentialsException.class);

        assertThat(user.getPassword()).isEqualTo("encoded-old");
    }

    @Test
    void updateUser_새비밀번호는_있는데_현재비밀번호가_없으면_InvalidCredentialsException을_던진다() {
        User user = User.createUser("user@test.com", "encoded-old", "닉네임");
        when(userRepository.findById(1L)).thenReturn(Optional.of(user));

        assertThatThrownBy(() -> userService.updateUser(1L, new UpdateUserCommand(null, null, "New1234!")))
                .isInstanceOf(InvalidCredentialsException.class);

        assertThat(user.getPassword()).isEqualTo("encoded-old");
    }

    @Test
    void updateUser_아무것도_없으면_아무것도_바꾸지_않는다() {
        User user = User.createUser("user@test.com", "encoded", "닉네임");
        when(userRepository.findById(1L)).thenReturn(Optional.of(user));

        userService.updateUser(1L, new UpdateUserCommand(null, null, null));

        assertThat(user.getNickname()).isEqualTo("닉네임");
        assertThat(user.getPassword()).isEqualTo("encoded");
    }

    @Test
    void withdraw_존재하지_않으면_UserNotFoundException을_던진다() {
        when(userRepository.findById(1L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> userService.withdraw(1L, new WithdrawCommand("pw")))
                .isInstanceOf(UserNotFoundException.class);
    }

    @Test
    void withdraw_비밀번호가_불일치하면_InvalidCredentialsException을_던지고_탈퇴처리하지_않는다() {
        User user = User.createUser("user@test.com", "encoded", "닉네임");
        when(userRepository.findById(1L)).thenReturn(Optional.of(user));
        when(passwordEncoder.matches("wrong", "encoded")).thenReturn(false);

        assertThatThrownBy(() -> userService.withdraw(1L, new WithdrawCommand("wrong")))
                .isInstanceOf(InvalidCredentialsException.class);

        assertThat(user.getStatus()).isEqualTo(UserStatus.ACTIVE);
        verify(refreshTokenRepository, never()).revokeAllByUserId(anyLong());
    }

    @Test
    void withdraw_비밀번호가_일치하면_소프트삭제하고_토큰을_일괄_폐기한다() {
        User user = User.createUser("user@test.com", "encoded", "닉네임");
        when(userRepository.findById(1L)).thenReturn(Optional.of(user));
        when(passwordEncoder.matches("correct", "encoded")).thenReturn(true);

        userService.withdraw(1L, new WithdrawCommand("correct"));

        assertThat(user.getStatus()).isEqualTo(UserStatus.WITHDRAWN);
        assertThat(user.getWithdrawnAt()).isNotNull();
        verify(refreshTokenRepository).revokeAllByUserId(eq(1L));
    }
}
