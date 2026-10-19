package com.jiseong.homesense.user.service;

import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.jiseong.homesense.auth.repository.RefreshTokenRepository;
import com.jiseong.homesense.auth.service.UserSessionRevoker;
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
    private final PasswordConfirmAttemptService passwordConfirmAttemptService;
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
     *   <li>D3 — 현재 비밀번호 확인({@link #confirmPassword}, 탈퇴와 공용 실패 잠금): 잠겼으면 429, 틀리면 카운트 +1 후 400
     *       {@code INVALID_CURRENT_PASSWORD}(D2), 맞으면 카운트 삭제.</li>
     *   <li>D4 — 새 비밀번호 저장과 함께 이 회원의 모든 세션을 끝낸다({@link UserSessionRevoker}, 비밀번호 재설정과 같은 경로·
     *       같은 원자성). 이 요청을 보낸 기기도 재로그인해야 한다.</li>
     * </ul>
     */
    public UserResponse updateUser(Long userId, UpdateUserCommand cmd) {
        User user = findUser(userId);
        boolean changingPassword = cmd.newPassword() != null;

        if (changingPassword) {
            confirmPassword(userId, cmd.currentPassword(), user);
        }

        if (cmd.nickname() != null) {
            user.changeNickname(cmd.nickname());
        }

        if (changingPassword) {
            user.changePassword(passwordEncoder.encode(cmd.newPassword()));
            userSessionRevoker.revokeAllSessions(userId);
        }

        return UserResponse.from(user);
    }

    /**
     * 탈퇴(소프트 삭제). 비밀번호 재확인은 비밀번호 변경과 같은 규칙이다 — 확인 전에 공용 실패 잠금을 보고(잠겼으면 429), 틀리면
     * 카운트를 올리고 400 {@code INVALID_CURRENT_PASSWORD}, 맞으면 카운트를 지운다. 탈퇴만 잠금 없는 확인 경로로 남으면 맞힌 뒤
     * /api/auth/reactivate로 계정을 되살려 비밀번호 변경 잠금(D3)을 우회할 수 있다(CLAUDE.md "SVC-USER-01 회원정보 수정 보강" 절).
     */
    public void withdraw(Long userId, WithdrawCommand cmd) {
        User user = findUser(userId);

        confirmPassword(userId, cmd.password(), user);

        user.withdraw(withdrawalPolicy.now());
        refreshTokenRepository.revokeAllByUserId(userId);
        userStatusCacheService.setStatus(userId, UserStatus.WITHDRAWN);
    }

    /**
     * 로그인한 회원의 비밀번호 확인(비밀번호 변경·탈퇴 공용). 비교 전에 시도를 원자적으로 예약한다 — 잠겼으면 {@code matches()}를
     * 부르지 않고 429. 예약한 시도는 성공·실패와 무관하게 세어져 틀리면 400, 맞아도 카운터를 지우지 않는다(잠금은 TTL로만
     * 풀린다 — PasswordConfirmAttemptService 참고). 동시 요청이 몇 개든 비교까지 가는 시도는 임계치(5회)를 넘지 않는다. null은 DTO 검증이 막는다(currentPassword는 @CurrentPasswordRequired, 탈퇴
     * password는 @NotBlank) — 여기 null은 내부 호출에 대한 방어다.
     */
    private void confirmPassword(Long userId, String rawPassword, User user) {
        if (!passwordConfirmAttemptService.tryAcquire(userId)) {
            throw new PasswordConfirmLockedException();
        }
        if (rawPassword == null || !passwordEncoder.matches(rawPassword, user.getPassword())) {
            throw new InvalidCurrentPasswordException();
        }
    }

    private User findUser(Long userId) {
        return userRepository.findById(userId).orElseThrow(UserNotFoundException::new);
    }
}
