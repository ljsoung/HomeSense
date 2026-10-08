package com.jiseong.homesense.notification.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.Set;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InOrder;
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
import com.jiseong.homesense.notification.exception.DuplicateNotificationTargetException;
import com.jiseong.homesense.notification.exception.InvalidNotificationTargetException;
import com.jiseong.homesense.notification.exception.MissingTargetException;
import com.jiseong.homesense.notification.exception.NotificationNotFoundException;
import com.jiseong.homesense.notification.repository.NotificationRepository;
import com.jiseong.homesense.notification.repository.NotificationSettingRepository;
import com.jiseong.homesense.user.entity.User;
import com.jiseong.homesense.user.repository.UserRepository;

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
    @Mock
    private UserRepository userRepository;

    private NotificationService notificationService;

    @BeforeEach
    void setUp() {
        notificationService = new NotificationService(notificationSettingRepository, notificationRepository,
                favoritePropertyRepository, favoriteRegionRepository, userRepository);
    }

    // ---- updateSettings: 대상 검증 ----

    private static UpdateNotificationSettingsCommand.Item property(Long id) {
        return new UpdateNotificationSettingsCommand.Item(id, null, new BigDecimal("5"), true, true);
    }

    private static UpdateNotificationSettingsCommand.Item region(Long id) {
        return new UpdateNotificationSettingsCommand.Item(null, id, new BigDecimal("5"), true, true);
    }

    private static UpdateNotificationSettingsCommand command(UpdateNotificationSettingsCommand.Item... items) {
        return new UpdateNotificationSettingsCommand(List.of(items));
    }

    private static FavoriteProperty ownedProperty(Long id, Long ownerId) {
        User owner = mock(User.class);
        when(owner.getUserId()).thenReturn(ownerId);
        FavoriteProperty property = mock(FavoriteProperty.class);
        when(property.getFavoritePropertyId()).thenReturn(id);
        when(property.getUser()).thenReturn(owner);
        return property;
    }

    private static FavoriteRegion ownedRegion(Long id, Long ownerId) {
        User owner = mock(User.class);
        when(owner.getUserId()).thenReturn(ownerId);
        FavoriteRegion region = mock(FavoriteRegion.class);
        when(region.getFavoriteRegionId()).thenReturn(id);
        when(region.getUser()).thenReturn(owner);
        return region;
    }

    private void verifyNothingSaved() {
        verify(notificationSettingRepository, never())
                .upsert(any(), any(), any(), any(), anyBoolean(), anyBoolean(), any());
    }

    @Test
    void updateSettings_두_대상_모두_지정되면_InvalidNotificationTargetException을_던진다() {
        UpdateNotificationSettingsCommand cmd = command(
                new UpdateNotificationSettingsCommand.Item(10L, 20L, new BigDecimal("5"), true, true));

        assertThatThrownBy(() -> notificationService.updateSettings(1L, cmd))
                .isInstanceOf(InvalidNotificationTargetException.class);
        verifyNothingSaved();
    }

    @Test
    void updateSettings_대상이_없으면_MissingTargetException을_던진다() {
        UpdateNotificationSettingsCommand cmd = command(
                new UpdateNotificationSettingsCommand.Item(null, null, new BigDecimal("5"), true, true));

        assertThatThrownBy(() -> notificationService.updateSettings(1L, cmd))
                .isInstanceOf(MissingTargetException.class);
        verifyNothingSaved();
    }

    @Test
    void updateSettings_같은_대상이_두_번_있으면_DuplicateNotificationTargetException을_던진다() {
        assertThatThrownBy(() -> notificationService.updateSettings(1L, command(property(100L), region(200L), property(100L))))
                .isInstanceOf(DuplicateNotificationTargetException.class);
        verifyNothingSaved();
    }

    @Test
    void updateSettings_매물과_지역의_id가_같아도_서로_다른_대상이다() {
        FavoriteProperty property = ownedProperty(7L, 1L);
        FavoriteRegion region = ownedRegion(7L, 1L);
        when(favoritePropertyRepository.findAllById(Set.of(7L))).thenReturn(List.of(property));
        when(favoriteRegionRepository.findAllById(Set.of(7L))).thenReturn(List.of(region));

        notificationService.updateSettings(1L, command(property(7L), region(7L)));

        verify(notificationSettingRepository, times(2))
                .upsert(eq(1L), any(), any(), any(), anyBoolean(), anyBoolean(), any());
    }

    @Test
    void updateSettings_존재하지_않는_favoritePropertyId면_FavoriteNotFoundException을_던지고_저장하지_않는다() {
        when(favoritePropertyRepository.findAllById(Set.of(100L))).thenReturn(List.of());

        assertThatThrownBy(() -> notificationService.updateSettings(1L, command(property(100L))))
                .isInstanceOf(FavoriteNotFoundException.class);
        verifyNothingSaved();
    }

    @Test
    void updateSettings_존재하지_않는_favoriteRegionId면_FavoriteNotFoundException을_던진다() {
        when(favoriteRegionRepository.findAllById(Set.of(200L))).thenReturn(List.of());

        assertThatThrownBy(() -> notificationService.updateSettings(1L, command(region(200L))))
                .isInstanceOf(FavoriteNotFoundException.class);
        verifyNothingSaved();
    }

    @Test
    void updateSettings_여러_건_중_하나라도_남의_대상이면_AccessDeniedException을_던지고_아무것도_저장하지_않는다() {
        FavoriteProperty mine = ownedProperty(100L, 1L);
        FavoriteProperty others = ownedProperty(101L, 2L);
        when(favoritePropertyRepository.findAllById(Set.of(100L, 101L))).thenReturn(List.of(mine, others));

        assertThatThrownBy(() -> notificationService.updateSettings(1L, command(property(100L), property(101L))))
                .isInstanceOf(AccessDeniedException.class);
        verifyNothingSaved();
    }

    @Test
    void updateSettings_소유자가_다른_favoriteRegion이면_AccessDeniedException을_던진다() {
        FavoriteRegion region = ownedRegion(200L, 2L);
        when(favoriteRegionRepository.findAllById(Set.of(200L))).thenReturn(List.of(region));

        assertThatThrownBy(() -> notificationService.updateSettings(1L, command(region(200L))))
                .isInstanceOf(AccessDeniedException.class);
        verifyNothingSaved();
    }

    // ---- updateSettings: upsert ----

    /**
     * "조회 → 있으면 UPDATE, 없으면 INSERT"를 애플리케이션에서 분기하지 않는다 — 존재 여부와 무관하게 항목마다
     * {@link NotificationSettingRepository#upsert} 하나(네이티브 {@code INSERT ... ON DUPLICATE KEY UPDATE})만 호출한다.
     * 그 SQL이 실제로 INSERT/UPDATE를 올바르게 처리하는지와 목록 전체가 한 트랜잭션으로 롤백되는지는 Mockito로 증명할 수
     * 없어 `NotificationServiceMariaDbIT`(Testcontainers)로 검증한다.
     */
    @Test
    void updateSettings_관심매물과_관심지역을_한_번에_받아_대상마다_upsert를_호출한다() {
        FavoriteProperty property = ownedProperty(100L, 1L);
        FavoriteRegion region = ownedRegion(200L, 1L);
        when(favoritePropertyRepository.findAllById(Set.of(100L))).thenReturn(List.of(property));
        when(favoriteRegionRepository.findAllById(Set.of(200L))).thenReturn(List.of(region));

        notificationService.updateSettings(1L, command(
                new UpdateNotificationSettingsCommand.Item(100L, null, new BigDecimal("5"), true, false),
                new UpdateNotificationSettingsCommand.Item(null, 200L, new BigDecimal("10"), false, true)));

        verify(notificationSettingRepository).upsert(eq(1L), eq(100L), isNull(), eq(new BigDecimal("5")), eq(true),
                eq(false), any(LocalDateTime.class));
        verify(notificationSettingRepository).upsert(eq(1L), isNull(), eq(200L), eq(new BigDecimal("10")), eq(false),
                eq(true), any(LocalDateTime.class));
    }

    /**
     * 요청 순서와 무관하게 관심 매물(id 오름차순) → 관심 지역(id 오름차순) 순서로 upsert한다 — 겹치는 대상을 다른 순서로 담은
     * 두 요청이 서로의 행 잠금을 기다려 교착하지 않게 한다(실제 교착 여부는 `NotificationServiceMariaDbIT`).
     */
    @Test
    void updateSettings_회원_행을_먼저_잠그고_요청_순서와_무관하게_매물_지역_순서로_id_오름차순_upsert한다() {
        List<FavoriteProperty> properties = List.of(ownedProperty(300L, 1L), ownedProperty(100L, 1L));
        List<FavoriteRegion> regions = List.of(ownedRegion(200L, 1L), ownedRegion(50L, 1L));
        when(favoritePropertyRepository.findAllById(Set.of(300L, 100L))).thenReturn(properties);
        when(favoriteRegionRepository.findAllById(Set.of(200L, 50L))).thenReturn(regions);

        notificationService.updateSettings(1L, command(region(200L), property(300L), region(50L), property(100L)));

        InOrder inOrder = inOrder(userRepository, notificationSettingRepository);
        inOrder.verify(userRepository).lockForUpdate(1L);
        inOrder.verify(notificationSettingRepository).upsert(eq(1L), eq(100L), isNull(), any(), anyBoolean(), anyBoolean(), any());
        inOrder.verify(notificationSettingRepository).upsert(eq(1L), eq(300L), isNull(), any(), anyBoolean(), anyBoolean(), any());
        inOrder.verify(notificationSettingRepository).upsert(eq(1L), isNull(), eq(50L), any(), anyBoolean(), anyBoolean(), any());
        inOrder.verify(notificationSettingRepository).upsert(eq(1L), isNull(), eq(200L), any(), anyBoolean(), anyBoolean(), any());
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
        when(notification.getCreatedAt()).thenReturn(LocalDateTime.now());
        Pageable pageable = PageRequest.of(0, 10);
        Page<Notification> page = new PageImpl<>(List.of(notification), pageable, 1);
        when(notificationRepository.findByUser_UserIdOrderByCreatedAtDescNotificationIdDesc(1L, pageable)).thenReturn(page);

        Page<NotificationResponse> result = notificationService.getNotifications(1L, null, pageable);

        assertThat(result.getContent()).hasSize(1);
        verify(notificationRepository, never())
                .findByUser_UserIdAndNotificationTypeOrderByCreatedAtDescNotificationIdDesc(any(), any(), any());
    }

    @Test
    void getNotifications_필터가_있으면_유형별로_조회한다() {
        Pageable pageable = PageRequest.of(0, 10);
        Page<Notification> page = new PageImpl<>(List.of(), pageable, 0);
        when(notificationRepository.findByUser_UserIdAndNotificationTypeOrderByCreatedAtDescNotificationIdDesc(
                1L, NotificationType.NEW_TRADE, pageable)).thenReturn(page);

        Page<NotificationResponse> result = notificationService.getNotifications(1L, NotificationType.NEW_TRADE, pageable);

        assertThat(result.getContent()).isEmpty();
        verify(notificationRepository, never()).findByUser_UserIdOrderByCreatedAtDescNotificationIdDesc(any(), any());
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
