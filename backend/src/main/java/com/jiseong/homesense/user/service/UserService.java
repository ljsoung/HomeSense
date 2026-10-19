package com.jiseong.homesense.user.service;

import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.jiseong.homesense.auth.repository.RefreshTokenRepository;
import com.jiseong.homesense.auth.service.UserSessionRevoker;
import com.jiseong.homesense.common.exception.InvalidCredentialsException;
import com.jiseong.homesense.common.security.UserStatusCacheService;
import com.jiseong.homesense.user.dto.UpdateUserCommand;
import com.jiseong.homesense.user.dto.UserResponse;
import com.jiseong.homesense.user.dto.WithdrawCommand;
import com.jiseong.homesense.user.entity.User;
import com.jiseong.homesense.user.entity.UserStatus;
import com.jiseong.homesense.user.exception.InvalidCurrentPasswordException;
import com.jiseong.homesense.user.exception.PasswordChangeLockedException;
import com.jiseong.homesense.user.exception.UserNotFoundException;
import com.jiseong.homesense.user.repository.UserRepository;

import lombok.RequiredArgsConstructor;

/**
 * SVC-USER-01. 로그인 회원의 정보 조회·수정과 회원 탈퇴(소프트 삭제)를 담당한다. 탈퇴는 항상 소프트 삭제다 —
 * 유예기간 경과 후의 물리 삭제는 BAT-USR-01(WithdrawnUserPurgeScheduler)만 수행한다.
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
    private final WithdrawalPolicy withdrawalPolicy;
    private final UserStatusCacheService userStatusCacheService;
    private final PasswordChangeAttemptService passwordChangeAttemptService;
    private final UserSessionRevoker userSessionRevoker;

    @Transactional(readOnly = true)
    public UserResponse getUser(Long userId) {
        return UserResponse.from(findUser(userId));
    }

    /**
     * MY-05 회원정보 수정. 닉네임·비밀번호 모두 선택이고(null이면 건드리지 않음) 한 트랜잭션이다 — 비밀번호 확인이 실패하면
     * 닉네임도 저장되지 않는다(확인을 먼저 한다). 둘 다 없으면 변경 없이 현재 정보를 돌려준다.
     *
     * <p>비밀번호 변경(CLAUDE.md "SVC-USER-01 회원정보 수정 보강" 절):
     * <ul>
     *   <li>D3 — 현재 비밀번호 확인 전에 실패 잠금을 본다(잠겼으면 {@code matches()}를 부르지 않고 429). 불일치면 카운트를
     *       올리고 400 {@code INVALID_CURRENT_PASSWORD}(D2). 성공하면 카운트를 지운다.</li>
     *   <li>D4 — 새 비밀번호 저장과 함께 이 회원의 모든 세션을 끝낸다({@link UserSessionRevoker}, 비밀번호 재설정과 같은 경로·
     *       같은 원자성). 이 요청을 보낸 기기도 재로그인해야 한다.</li>
     * </ul>
     */
    public UserResponse updateUser(Long userId, UpdateUserCommand cmd) {
        User user = findUser(userId);
        boolean changingPassword = cmd.newPassword() != null;

        if (changingPassword) {
            if (passwordChangeAttemptService.isLocked(userId)) {
                throw new PasswordChangeLockedException();
            }
            // currentPassword 누락은 DTO(@CurrentPasswordRequired)가 막는다 — 여기 null은 내부 호출에 대한 방어다.
            if (cmd.currentPassword() == null || !passwordEncoder.matches(cmd.currentPassword(), user.getPassword())) {
                passwordChangeAttemptService.recordFailure(userId);
                throw new InvalidCurrentPasswordException();
            }
        }

        if (cmd.nickname() != null) {
            user.changeNickname(cmd.nickname());
        }

        if (changingPassword) {
            user.changePassword(passwordEncoder.encode(cmd.newPassword()));
            userSessionRevoker.revokeAllSessions(userId);
            passwordChangeAttemptService.reset(userId);
        }

        return UserResponse.from(user);
    }

    public void withdraw(Long userId, WithdrawCommand cmd) {
        User user = findUser(userId);

        if (!passwordEncoder.matches(cmd.password(), user.getPassword())) {
            throw new InvalidCredentialsException();
        }

        user.withdraw(withdrawalPolicy.now());
        refreshTokenRepository.revokeAllByUserId(userId);
        userStatusCacheService.setStatus(userId, UserStatus.WITHDRAWN);
    }

    private User findUser(Long userId) {
        return userRepository.findById(userId).orElseThrow(UserNotFoundException::new);
    }
}
