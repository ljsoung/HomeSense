package com.jiseong.homesense.notification.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
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
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.DataIntegrityViolationException;
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
    @Mock
    private NotificationSettingInsertGateway notificationSettingInsertGateway;

    private NotificationService notificationService;

    @BeforeEach
    void setUp() {
        notificationService = new NotificationService(notificationSettingRepository, notificationRepository,
                favoritePropertyRepository, favoriteRegionRepository, userRepository, notificationSettingInsertGateway);
    }

    // ---- updateSettings: 대상 검증 ----

    @Test
    void updateSettings_두_대상_모두_지정되면_InvalidNotificationTargetException을_던진다() {
        UpdateNotificationSettingsCommand cmd =
                new UpdateNotificationSettingsCommand(10L, 20L, new BigDecimal("5.0"), true, true);

        assertThatThrownBy(() -> notificationService.updateSettings(1L, cmd))
                .isInstanceOf(InvalidNotificationTargetException.class);
        verify(notificationSettingInsertGateway, never()).insert(any());
    }

    @Test
    void updateSettings_대상이_없으면_MissingTargetException을_던진다() {
        UpdateNotificationSettingsCommand cmd =
                new UpdateNotificationSettingsCommand(null, null, new BigDecimal("5.0"), true, true);

        assertThatThrownBy(() -> notificationService.updateSettings(1L, cmd))
                .isInstanceOf(MissingTargetException.class);
        verify(notificationSettingInsertGateway, never()).insert(any());
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
        verify(notificationSettingInsertGateway, never()).insert(any());
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
        verify(notificationSettingInsertGateway, never()).insert(any());
    }

    // ---- updateSettings: upsert ----

    @Test
    void updateSettings_기존설정이_없으면_새로_저장한다() {
        User owner = mock(User.class);
        when(owner.getUserId()).thenReturn(1L);
        FavoriteProperty property = mock(FavoriteProperty.class);
        when(property.getFavoritePropertyId()).thenReturn(100L);
        when(property.getUser()).thenReturn(owner);
        when(favoritePropertyRepository.findById(100L)).thenReturn(Optional.of(property));
        when(notificationSettingRepository.findByUser_UserIdAndFavoriteProperty_FavoritePropertyId(1L, 100L))
                .thenReturn(Optional.empty());
        when(userRepository.getReferenceById(1L)).thenReturn(owner);

        UpdateNotificationSettingsCommand cmd =
                new UpdateNotificationSettingsCommand(100L, null, new BigDecimal("5.0"), true, false);
        notificationService.updateSettings(1L, cmd);

        verify(notificationSettingInsertGateway).insert(any(NotificationSetting.class));
    }

    @Test
    void updateSettings_기존설정이_있으면_갱신만_하고_새로_저장하지_않는다() {
        User owner = mock(User.class);
        when(owner.getUserId()).thenReturn(1L);
        FavoriteRegion region = mock(FavoriteRegion.class);
        when(region.getFavoriteRegionId()).thenReturn(200L);
        when(region.getUser()).thenReturn(owner);
        when(favoriteRegionRepository.findById(200L)).thenReturn(Optional.of(region));
        when(userRepository.getReferenceById(1L)).thenReturn(owner);

        NotificationSetting existing = NotificationSetting.forRegion(owner, region, new BigDecimal("5.0"), false, false);
        when(notificationSettingRepository.findByUser_UserIdAndFavoriteRegion_FavoriteRegionId(1L, 200L))
                .thenReturn(Optional.of(existing));

        UpdateNotificationSettingsCommand cmd =
                new UpdateNotificationSettingsCommand(null, 200L, new BigDecimal("10.0"), true, true);
        notificationService.updateSettings(1L, cmd);

        assertThat(existing.getPriceChangeThresholdPct()).isEqualByComparingTo("10.0");
        assertThat(existing.isNewTradeAlertYn()).isTrue();
        assertThat(existing.isEmailAlertYn()).isTrue();
        verify(notificationSettingInsertGateway, never()).insert(any());
    }

    /**
     * findByXxx() 조회 → INSERT 시도하는 흐름이라, 조회 이후 INSERT 이전에 다른 요청이 같은 대상으로
     * 먼저 커밋을 끝낸 race condition을 DataIntegrityViolationException으로 재현한다 — 이 API는
     * "등록 거부"가 아니라 "upsert"라 DuplicateXxxException으로 변환하지 않고 먼저 커밋된 값을
     * 재조회해 요청받은 조건으로 갱신한다. INSERT 시도는 NotificationSettingInsertGateway가 REQUIRES_NEW로
     * 격리하므로 실패해도 updateSettings()의 트랜잭션은 오염되지 않아 이 재조회가 안전하다
     * (CLAUDE.md SVC-NTF-01 절 참고 — TradeChunkLoader.upsertOne()과 같은 이유).
     */
    @Test
    void updateSettings_INSERT시점에_UNIQUE_위반이_발생하면_재조회하여_갱신한다() {
        User owner = mock(User.class);
        when(owner.getUserId()).thenReturn(1L);
        FavoriteProperty property = mock(FavoriteProperty.class);
        when(property.getFavoritePropertyId()).thenReturn(100L);
        when(property.getUser()).thenReturn(owner);
        when(favoritePropertyRepository.findById(100L)).thenReturn(Optional.of(property));
        when(userRepository.getReferenceById(1L)).thenReturn(owner);

        NotificationSetting winner = NotificationSetting.forProperty(owner, property, new BigDecimal("3.0"), false, false);
        when(notificationSettingRepository.findByUser_UserIdAndFavoriteProperty_FavoritePropertyId(1L, 100L))
                .thenReturn(Optional.empty(), Optional.of(winner));
        doThrow(new DataIntegrityViolationException("dup")).when(notificationSettingInsertGateway).insert(any());

        UpdateNotificationSettingsCommand cmd =
                new UpdateNotificationSettingsCommand(100L, null, new BigDecimal("7.5"), true, true);
        notificationService.updateSettings(1L, cmd);

        assertThat(winner.getPriceChangeThresholdPct()).isEqualByComparingTo("7.5");
        assertThat(winner.isNewTradeAlertYn()).isTrue();
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
