package com.jiseong.homesense.user.service;

import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.jiseong.homesense.auth.repository.RefreshTokenRepository;
import com.jiseong.homesense.common.exception.InvalidCredentialsException;
import com.jiseong.homesense.user.dto.UpdateUserCommand;
import com.jiseong.homesense.user.dto.UserResponse;
import com.jiseong.homesense.user.dto.WithdrawCommand;
import com.jiseong.homesense.user.entity.User;
import com.jiseong.homesense.user.exception.UserNotFoundException;
import com.jiseong.homesense.user.repository.UserRepository;

import lombok.RequiredArgsConstructor;

/**
 * SVC-USER-01. 로그인 회원의 정보 조회·수정과 회원 탈퇴(소프트 삭제)를 담당한다.
 * userId는 항상 인증된 UserPrincipal(Access Token)에서 나오므로, 여기서 조회가 비면 정상적인
 * 사용자 흐름이 아니라 토큰 위조 등 비정상 상황으로 본다(UserNotFoundException).
 */
@Service
@RequiredArgsConstructor
@Transactional
public class UserService {

    private final UserRepository userRepository;
    private final RefreshTokenRepository refreshTokenRepository;
    private final PasswordEncoder passwordEncoder;

    @Transactional(readOnly = true)
    public UserResponse getUser(Long userId) {
        return UserResponse.from(findUser(userId));
    }

    public UserResponse updateUser(Long userId, UpdateUserCommand cmd) {
        User user = findUser(userId);

        if (cmd.nickname() != null) {
            user.changeNickname(cmd.nickname());
        }

        if (cmd.newPassword() != null) {
            if (cmd.currentPassword() == null || !passwordEncoder.matches(cmd.currentPassword(), user.getPassword())) {
                throw new InvalidCredentialsException();
            }
            user.changePassword(passwordEncoder.encode(cmd.newPassword()));
        }

        return UserResponse.from(user);
    }

    public void withdraw(Long userId, WithdrawCommand cmd) {
        User user = findUser(userId);

        if (!passwordEncoder.matches(cmd.password(), user.getPassword())) {
            throw new InvalidCredentialsException();
        }

        user.withdraw();
        refreshTokenRepository.revokeAllByUserId(userId);
    }

    private User findUser(Long userId) {
        return userRepository.findById(userId).orElseThrow(UserNotFoundException::new);
    }
}
