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
    void 캐시미스면_Redis가_재시작된_경우처럼_DB에서_상태를_읽어_반환하고_캐시를_다시_채운다() {
        when(userStatusCacheService.getStatus(1L)).thenReturn(Optional.empty());
        User user = User.createUser("user@test.com", "encoded", "닉네임");
        when(userRepository.findById(1L)).thenReturn(Optional.of(user));

        assertThat(resolver.isActive(1L)).isTrue();
        verify(userStatusCacheService).setStatus(1L, UserStatus.ACTIVE);
    }

    @Test
    void 캐시미스이고_DB_상태가_ACTIVE가_아니면_그_상태로_캐시를_채우고_false를_반환한다() {
        when(userStatusCacheService.getStatus(1L)).thenReturn(Optional.empty());
        User user = User.createUser("user@test.com", "encoded", "닉네임");
        user.withdraw(LocalDateTime.now());
        when(userRepository.findById(1L)).thenReturn(Optional.of(user));

        assertThat(resolver.isActive(1L)).isFalse();
        verify(userStatusCacheService).setStatus(1L, UserStatus.WITHDRAWN);
    }

    @Test
    void 캐시미스이고_DB에도_사용자가_없으면_미인증으로_처리하고_캐시를_쓰지_않는다() {
        // 유예기간 경과 후 파기(BAT-USR-01)된 계정 — 캐시 인프라 문제가 아니라 실제로 존재하지 않는 계정이다.
        when(userStatusCacheService.getStatus(1L)).thenReturn(Optional.empty());
        when(userRepository.findById(1L)).thenReturn(Optional.empty());

        assertThat(resolver.isActive(1L)).isFalse();
        verify(userStatusCacheService, never()).setStatus(anyLong(), any());
    }
}
