package com.jiseong.homesense.notification.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;

import com.jiseong.homesense.favorite.entity.FavoriteProperty;
import com.jiseong.homesense.favorite.entity.FavoriteRegion;
import com.jiseong.homesense.favorite.exception.FavoriteNotFoundException;
import com.jiseong.homesense.favorite.repository.FavoritePropertyRepository;
import com.jiseong.homesense.favorite.repository.FavoriteRegionRepository;
import com.jiseong.homesense.notification.dto.NotificationResponse;
import com.jiseong.homesense.notification.dto.NotificationSettingResponse;
import com.jiseong.homesense.notification.dto.UpdateNotificationSettingsCommand;
import com.jiseong.homesense.notification.entity.Notification;
import com.jiseong.homesense.notification.entity.NotificationSetting;
import com.jiseong.homesense.notification.entity.NotificationType;
import com.jiseong.homesense.notification.exception.AccessDeniedException;
import com.jiseong.homesense.notification.exception.InvalidNotificationTargetException;
import com.jiseong.homesense.notification.exception.MissingTargetException;
import com.jiseong.homesense.notification.exception.NotificationNotFoundException;
import com.jiseong.homesense.notification.repository.NotificationRepository;
import com.jiseong.homesense.notification.repository.NotificationSettingRepository;
import com.jiseong.homesense.user.entity.User;

@ExtendWith(MockitoExtension.class)
class NotificationServiceTest {

    @Mock
    private NotificationSettingRepository notificationSettingRepository;
    @Mock
    private NotificationRepository notificationRepository;
    @Mock
    private FavoritePropertyRepository favoritePropertyRepository;
    @Mock
    private FavoriteRegionRepository favoriteRegionRepository;

    private NotificationService notificationService;

    @BeforeEach
    void setUp() {
        notificationService = new NotificationService(notificationSettingRepository, notificationRepository,
                favoritePropertyRepository, favoriteRegionRepository);
    }

    // ---- updateSettings: 대상 검증 ----

    @Test
    void updateSettings_두_대상_모두_지정되면_InvalidNotificationTargetException을_던진다() {
        UpdateNotificationSettingsCommand cmd =
                new UpdateNotificationSettingsCommand(10L, 20L, new BigDecimal("5.0"), true, true);

        assertThatThrownBy(() -> notificationService.updateSettings(1L, cmd))
                .isInstanceOf(InvalidNotificationTargetException.class);
        verify(notificationSettingRepository, never())
                .upsert(any(), any(), any(), any(), anyBoolean(), anyBoolean(), any());
    }

    @Test
    void updateSettings_대상이_없으면_MissingTargetException을_던진다() {
        UpdateNotificationSettingsCommand cmd =
                new UpdateNotificationSettingsCommand(null, null, new BigDecimal("5.0"), true, true);

        assertThatThrownBy(() -> notificationService.updateSettings(1L, cmd))
                .isInstanceOf(MissingTargetException.class);
        verify(notificationSettingRepository, never())
                .upsert(any(), any(), any(), any(), anyBoolean(), anyBoolean(), any());
    }

    @Test
    void updateSettings_존재하지_않는_favoritePropertyId면_FavoriteNotFoundException을_던진다() {
        when(favoritePropertyRepository.findById(100L)).thenReturn(Optional.empty());
        UpdateNotificationSettingsCommand cmd =
                new UpdateNotificationSettingsCommand(100L, null, new BigDecimal("5.0"), true, true);

        assertThatThrownBy(() -> notificationService.updateSettings(1L, cmd))
                .isInstanceOf(FavoriteNotFoundException.class);
    }

    @Test
    void updateSettings_소유자가_다른_favoriteProperty면_AccessDeniedException을_던지고_저장하지_않는다() {
        User owner = mock(User.class);
        when(owner.getUserId()).thenReturn(2L);
        FavoriteProperty property = mock(FavoriteProperty.class);
        when(property.getUser()).thenReturn(owner);
        when(favoritePropertyRepository.findById(100L)).thenReturn(Optional.of(property));

        UpdateNotificationSettingsCommand cmd =
                new UpdateNotificationSettingsCommand(100L, null, new BigDecimal("5.0"), true, true);

        assertThatThrownBy(() -> notificationService.updateSettings(1L, cmd))
                .isInstanceOf(AccessDeniedException.class);
        verify(notificationSettingRepository, never())
                .upsert(any(), any(), any(), any(), anyBoolean(), anyBoolean(), any());
    }

    @Test
    void updateSettings_존재하지_않는_favoriteRegionId면_FavoriteNotFoundException을_던진다() {
        when(favoriteRegionRepository.findById(200L)).thenReturn(Optional.empty());
        UpdateNotificationSettingsCommand cmd =
                new UpdateNotificationSettingsCommand(null, 200L, new BigDecimal("5.0"), true, true);

        assertThatThrownBy(() -> notificationService.updateSettings(1L, cmd))
                .isInstanceOf(FavoriteNotFoundException.class);
    }

    @Test
    void updateSettings_소유자가_다른_favoriteRegion이면_AccessDeniedException을_던진다() {
        User owner = mock(User.class);
        when(owner.getUserId()).thenReturn(2L);
        FavoriteRegion region = mock(FavoriteRegion.class);
        when(region.getUser()).thenReturn(owner);
        when(favoriteRegionRepository.findById(200L)).thenReturn(Optional.of(region));

        UpdateNotificationSettingsCommand cmd =
                new UpdateNotificationSettingsCommand(null, 200L, new BigDecimal("5.0"), true, true);

        assertThatThrownBy(() -> notificationService.updateSettings(1L, cmd))
                .isInstanceOf(AccessDeniedException.class);
        verify(notificationSettingRepository, never())
                .upsert(any(), any(), any(), any(), anyBoolean(), anyBoolean(), any());
    }

    // ---- updateSettings: upsert ----

    /**
     * "조회 → 있으면 UPDATE, 없으면 INSERT"를 애플리케이션에서 분기하지 않는다 — 존재 여부와 무관하게
     * 항상 {@link NotificationSettingRepository#upsert} 하나(네이티브 {@code INSERT ... ON DUPLICATE
     * KEY UPDATE})만 호출한다. 최초 구현은 존재 여부를 먼저 조회해 분기했었는데, 그 재조회가 MariaDB
     * 기본 격리수준(REPEATABLE READ)의 트랜잭션 스냅샷에 묶여 경쟁에서 이긴 다른 트랜잭션의 커밋을
     * 보지 못하는 문제가 있었다(Codex 코드리뷰 P1 지적, CLAUDE.md SVC-NTF-01 절 참고) — 원자적 upsert로
     * 바꿔 이 문제 자체를 제거했다. 그 SQL 문장이 실제로 INSERT/UPDATE 둘 다 올바르게 처리하는지는
     * Mockito로 증명할 수 없어 `NotificationServiceMariaDbIT`(Testcontainers)로 별도 검증한다.
     */
    @Test
    void updateSettings_관심매물_대상이면_userId와_favoritePropertyId로_upsert를_호출한다() {
        User owner = mock(User.class);
        when(owner.getUserId()).thenReturn(1L);
        FavoriteProperty property = mock(FavoriteProperty.class);
        when(property.getUser()).thenReturn(owner);
        when(favoritePropertyRepository.findById(100L)).thenReturn(Optional.of(property));

        UpdateNotificationSettingsCommand cmd =
                new UpdateNotificationSettingsCommand(100L, null, new BigDecimal("5.0"), true, false);
        notificationService.updateSettings(1L, cmd);

        ArgumentCaptor<Long> favoritePropertyIdCaptor = ArgumentCaptor.forClass(Long.class);
        ArgumentCaptor<Long> favoriteRegionIdCaptor = ArgumentCaptor.forClass(Long.class);
        verify(notificationSettingRepository).upsert(eq(1L), favoritePropertyIdCaptor.capture(),
                favoriteRegionIdCaptor.capture(), eq(new BigDecimal("5.0")), eq(true), eq(false), any());
        assertThat(favoritePropertyIdCaptor.getValue()).isEqualTo(100L);
        assertThat(favoriteRegionIdCaptor.getValue()).isNull();
    }

    @Test
    void updateSettings_관심지역_대상이면_userId와_favoriteRegionId로_upsert를_호출한다() {
        User owner = mock(User.class);
        when(owner.getUserId()).thenReturn(1L);
        FavoriteRegion region = mock(FavoriteRegion.class);
        when(region.getUser()).thenReturn(owner);
        when(favoriteRegionRepository.findById(200L)).thenReturn(Optional.of(region));

        UpdateNotificationSettingsCommand cmd =
                new UpdateNotificationSettingsCommand(null, 200L, new BigDecimal("10.0"), true, true);
        notificationService.updateSettings(1L, cmd);

        ArgumentCaptor<Long> favoritePropertyIdCaptor = ArgumentCaptor.forClass(Long.class);
        ArgumentCaptor<Long> favoriteRegionIdCaptor = ArgumentCaptor.forClass(Long.class);
        verify(notificationSettingRepository).upsert(eq(1L), favoritePropertyIdCaptor.capture(),
                favoriteRegionIdCaptor.capture(), eq(new BigDecimal("10.0")), eq(true), eq(true), any());
        assertThat(favoritePropertyIdCaptor.getValue()).isNull();
        assertThat(favoriteRegionIdCaptor.getValue()).isEqualTo(200L);
    }

    // ---- getSettings ----

    @Test
    void getSettings_설정목록을_응답으로_변환한다() {
        User owner = mock(User.class);
        FavoriteProperty property = mock(FavoriteProperty.class);
        when(property.getFavoritePropertyId()).thenReturn(100L);
        NotificationSetting setting = NotificationSetting.forProperty(owner, property, new BigDecimal("5.0"), true, false);
        when(notificationSettingRepository.findByUser_UserId(1L)).thenReturn(List.of(setting));

        List<NotificationSettingResponse> result = notificationService.getSettings(1L);

        assertThat(result).hasSize(1);
        assertThat(result.get(0).favoritePropertyId()).isEqualTo(100L);
        assertThat(result.get(0).favoriteRegionId()).isNull();
        assertThat(result.get(0).priceChangeThresholdPct()).isEqualByComparingTo("5.0");
        assertThat(result.get(0).newTradeAlertYn()).isTrue();
    }

    // ---- getNotifications ----

    @Test
    void getNotifications_필터가_없으면_전체_조회한다() {
        Notification notification = mock(Notification.class);
        when(notification.getSentAt()).thenReturn(LocalDateTime.now());
        Pageable pageable = PageRequest.of(0, 10);
        Page<Notification> page = new PageImpl<>(List.of(notification), pageable, 1);
        when(notificationRepository.findByUser_UserIdOrderBySentAtDesc(1L, pageable)).thenReturn(page);

        Page<NotificationResponse> result = notificationService.getNotifications(1L, null, pageable);

        assertThat(result.getContent()).hasSize(1);
        verify(notificationRepository, never())
                .findByUser_UserIdAndNotificationTypeOrderBySentAtDesc(any(), any(), any());
    }

    @Test
    void getNotifications_필터가_있으면_유형별로_조회한다() {
        Pageable pageable = PageRequest.of(0, 10);
        Page<Notification> page = new PageImpl<>(List.of(), pageable, 0);
        when(notificationRepository.findByUser_UserIdAndNotificationTypeOrderBySentAtDesc(
                1L, NotificationType.NEW_TRADE, pageable)).thenReturn(page);

        Page<NotificationResponse> result = notificationService.getNotifications(1L, NotificationType.NEW_TRADE, pageable);

        assertThat(result.getContent()).isEmpty();
        verify(notificationRepository, never()).findByUser_UserIdOrderBySentAtDesc(any(), any());
    }

    // ---- markAsRead ----

    @Test
    void markAsRead_존재하지_않으면_NotificationNotFoundException을_던진다() {
        when(notificationRepository.findById(1L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> notificationService.markAsRead(1L, 1L))
                .isInstanceOf(NotificationNotFoundException.class);
    }

    @Test
    void markAsRead_소유자가_다르면_AccessDeniedException을_던지고_읽음처리하지_않는다() {
        User owner = mock(User.class);
        when(owner.getUserId()).thenReturn(2L);
        Notification notification = mock(Notification.class);
        when(notification.getUser()).thenReturn(owner);
        when(notificationRepository.findById(1L)).thenReturn(Optional.of(notification));

        assertThatThrownBy(() -> notificationService.markAsRead(1L, 1L))
                .isInstanceOf(AccessDeniedException.class);
        verify(notification, never()).markAsRead();
    }

    @Test
    void markAsRead_소유자가_일치하면_읽음처리한다() {
        User owner = mock(User.class);
        when(owner.getUserId()).thenReturn(1L);
        Notification notification = mock(Notification.class);
        when(notification.getUser()).thenReturn(owner);
        when(notificationRepository.findById(1L)).thenReturn(Optional.of(notification));

        notificationService.markAsRead(1L, 1L);

        verify(notification).markAsRead();
    }
}
