package com.jiseong.homesense.common.security;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
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

import com.jiseong.homesense.user.entity.User;
import com.jiseong.homesense.user.entity.UserStatus;
import com.jiseong.homesense.user.repository.UserRepository;

@ExtendWith(MockitoExtension.class)
class UserStatusResolverTest {

    @Mock
    private UserStatusCacheService userStatusCacheService;
    @Mock
    private UserRepository userRepository;

    private UserStatusResolver resolver;

    @BeforeEach
    void setUp() {
        resolver = new UserStatusResolver(userStatusCacheService, userRepository);
    }

    @Test
    void 캐시_히트면_DB를_조회하지_않고_캐시_값을_그대로_사용한다() {
        when(userStatusCacheService.getStatus(1L)).thenReturn(Optional.of(UserStatus.ACTIVE));

        assertThat(resolver.isActive(1L)).isTrue();
        verify(userRepository, never()).findById(anyLong());
    }

    @Test
    void 캐시_히트값이_ACTIVE가_아니면_false를_반환하고_DB를_조회하지_않는다() {
        when(userStatusCacheService.getStatus(1L)).thenReturn(Optional.of(UserStatus.WITHDRAWN));

        assertThat(resolver.isActive(1L)).isFalse();
        verify(userRepository, never()).findById(anyLong());
    }

    @Test
    void 캐시미스면_Redis가_재시작된_경우처럼_DB에서_상태를_읽어_반환하고_setIfAbsent로_캐시를_다시_채운다() {
        when(userStatusCacheService.getStatus(1L)).thenReturn(Optional.empty());
        User user = User.createUser("user@test.com", "encoded", "닉네임");
        when(userRepository.findById(1L)).thenReturn(Optional.of(user));

        assertThat(resolver.isActive(1L)).isTrue();
        verify(userStatusCacheService).setIfAbsent(1L, UserStatus.ACTIVE);
        verify(userStatusCacheService, never()).setStatus(anyLong(), any());
    }

    @Test
    void 캐시미스이고_DB_상태가_ACTIVE가_아니면_그_상태로_setIfAbsent를_시도하고_false를_반환한다() {
        when(userStatusCacheService.getStatus(1L)).thenReturn(Optional.empty());
        User user = User.createUser("user@test.com", "encoded", "닉네임");
        user.withdraw(LocalDateTime.now());
        when(userRepository.findById(1L)).thenReturn(Optional.of(user));

        assertThat(resolver.isActive(1L)).isFalse();
        verify(userStatusCacheService).setIfAbsent(1L, UserStatus.WITHDRAWN);
    }

    @Test
    void 캐시미스이고_DB에도_사용자가_없으면_미인증으로_처리하고_캐시를_쓰지_않는다() {
        // 유예기간 경과 후 파기(BAT-USR-01)된 계정 — 캐시 인프라 문제가 아니라 실제로 존재하지 않는 계정이다.
        when(userStatusCacheService.getStatus(1L)).thenReturn(Optional.empty());
        when(userRepository.findById(1L)).thenReturn(Optional.empty());

        assertThat(resolver.isActive(1L)).isFalse();
        verify(userStatusCacheService, never()).setIfAbsent(anyLong(), any());
    }

    /*
     * 코드리뷰 P1 지적(2026-09-22) — 이 resolver의 DB 읽기도 "읽고 나서 늦게 쓰는" 패턴이라
     * AuthService가 겪었던 것과 같은 모양의 race를 좁은 창에서 재현할 수 있다: T1에 이 resolver가
     * DB에서 ACTIVE를 읽고, T2에 동시 실행된 withdraw()가 DB+캐시에 WITHDRAWN을 먼저 반영하고,
     * T3(T2보다 늦음)에 이 resolver가 뒤늦게 재개돼 캐시에 쓰려 한다 — setIfAbsent이므로 이 시도는
     * no-op이어야 하고(WITHDRAWN 보존), 그와 별개로 이 요청 자신의 인가 판단은 자신이 실제로 읽은
     * 값(ACTIVE)을 그대로 쓴다 — 이미 진행 중인 요청 하나의 판단을 소급 취소할 방법은 없고, 이
     * 테스트가 보장하려는 것은 "공유 캐시가 오염되지 않는다"는 것이지 "이 한 요청도 즉시 막힌다"가
     * 아니다.
     */
    @Test
    void 캐시미스_DB읽기_이후_경쟁으로_setIfAbsent가_지면_공유_캐시는_보존되지만_이_요청_자신의_판단은_그대로_반환한다() {
        when(userStatusCacheService.getStatus(1L)).thenReturn(Optional.empty());
        User user = User.createUser("user@test.com", "encoded", "닉네임");
        when(userRepository.findById(1L)).thenReturn(Optional.of(user));
        // 동시에 커밋된 withdraw()가 이미 WITHDRAWN을 선점해 둔 상황을 흉내낸다 — setIfAbsent가 진다.
        when(userStatusCacheService.setIfAbsent(1L, UserStatus.ACTIVE)).thenReturn(false);

        assertThat(resolver.isActive(1L)).isTrue();
        verify(userStatusCacheService).setIfAbsent(1L, UserStatus.ACTIVE);
        verify(userStatusCacheService, never()).setStatus(anyLong(), any());
    }
}
