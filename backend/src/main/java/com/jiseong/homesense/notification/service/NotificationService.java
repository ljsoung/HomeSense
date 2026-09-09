package com.jiseong.homesense.notification.service;

import java.util.List;
import java.util.Optional;

import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

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

import lombok.RequiredArgsConstructor;

/**
 * SVC-NTF-01. 관심 대상별 알림 조건(가격 변동 임계치, 신규거래 알림 여부) 설정 관리와, 발송된 알림
 * 이력 조회·읽음 처리를 담당한다. 실제 조건 평가·발송은 배치(BAT-NTF-01, BAT-MAIL-01)의 몫이라 이
 * 도메인은 CRUD만 다룬다. 캐시는 적용하지 않는다(회원별 개인화 데이터, FAV/RCV와 같은 이유).
 */
@Service
@RequiredArgsConstructor
@Transactional
public class NotificationService {

    private final NotificationSettingRepository notificationSettingRepository;
    private final NotificationRepository notificationRepository;
    private final FavoritePropertyRepository favoritePropertyRepository;
    private final FavoriteRegionRepository favoriteRegionRepository;
    private final UserRepository userRepository;
    private final NotificationSettingInsertGateway notificationSettingInsertGateway;

    @Transactional(readOnly = true)
    public List<NotificationSettingResponse> getSettings(Long userId) {
        return notificationSettingRepository.findByUser_UserId(userId).stream()
                .map(NotificationSettingResponse::from)
                .toList();
    }

    /**
     * favoritePropertyId/favoriteRegionId 중 정확히 하나가 가리키는 대상(반드시 본인 소유)에 대해
     * 기존 설정이 있으면 UPDATE, 없으면 INSERT한다.
     */
    public void updateSettings(Long userId, UpdateNotificationSettingsCommand cmd) {
        boolean hasProperty = cmd.favoritePropertyId() != null;
        boolean hasRegion = cmd.favoriteRegionId() != null;
        if (hasProperty && hasRegion) {
            throw new InvalidNotificationTargetException();
        }
        if (!hasProperty && !hasRegion) {
            throw new MissingTargetException();
        }

        User user = userRepository.getReferenceById(userId);
        if (hasProperty) {
            FavoriteProperty favoriteProperty = favoritePropertyRepository.findById(cmd.favoritePropertyId())
                    .orElseThrow(FavoriteNotFoundException::new);
            if (!favoriteProperty.getUser().getUserId().equals(userId)) {
                throw new AccessDeniedException();
            }
            upsertForProperty(user, favoriteProperty, cmd);
        } else {
            FavoriteRegion favoriteRegion = favoriteRegionRepository.findById(cmd.favoriteRegionId())
                    .orElseThrow(FavoriteNotFoundException::new);
            if (!favoriteRegion.getUser().getUserId().equals(userId)) {
                throw new AccessDeniedException();
            }
            upsertForRegion(user, favoriteRegion, cmd);
        }
    }

    @Transactional(readOnly = true)
    public Page<NotificationResponse> getNotifications(Long userId, NotificationType typeFilter, Pageable pageable) {
        Page<Notification> notifications = typeFilter != null
                ? notificationRepository.findByUser_UserIdAndNotificationTypeOrderBySentAtDesc(
                        userId, typeFilter, pageable)
                : notificationRepository.findByUser_UserIdOrderBySentAtDesc(userId, pageable);
        return notifications.map(NotificationResponse::from);
    }

    public void markAsRead(Long userId, Long notificationId) {
        Notification notification = notificationRepository.findById(notificationId)
                .orElseThrow(NotificationNotFoundException::new);
        if (!notification.getUser().getUserId().equals(userId)) {
            throw new AccessDeniedException();
        }
        notification.markAsRead();
    }

    private void upsertForProperty(User user, FavoriteProperty favoriteProperty,
            UpdateNotificationSettingsCommand cmd) {
        Optional<NotificationSetting> existing = notificationSettingRepository
                .findByUser_UserIdAndFavoriteProperty_FavoritePropertyId(
                        user.getUserId(), favoriteProperty.getFavoritePropertyId());
        if (existing.isPresent()) {
            existing.get().updateConditions(cmd.priceChangeThresholdPct(), cmd.newTradeAlertYn(), cmd.emailAlertYn());
            return;
        }

        NotificationSetting created = NotificationSetting.forProperty(
                user, favoriteProperty, cmd.priceChangeThresholdPct(), cmd.newTradeAlertYn(), cmd.emailAlertYn());
        try {
            // INSERT 시도만 별도 REQUIRES_NEW 트랜잭션(NotificationSettingInsertGateway)에서 실행한다 —
            // 이 메서드(updateSettings())와 같은 트랜잭션에서 곧바로 save()했다면 UNIQUE 위반으로 인한
            // flush 실패가 JPA 스펙상 트랜잭션을 rollback-only로 표시해, 아래 catch에서 시도하는 재조회·
            // 갱신이 커밋 시점에 UnexpectedRollbackException으로 무효화된다(TradeChunkLoader.upsertOne()/
            // TradeInsertGateway와 동일한 이유, CLAUDE.md SVC-NTF-01 절 참고). 이 API는 "등록 거부"가
            // 아니라 "설정값 upsert"라 race condition을 DuplicateXxxException으로 번역하지 않고, 먼저
            // 커밋된 값을 다시 조회해 요청받은 조건으로 갱신한다 — 최종 상태가 요청과 같기만 하면 되고
            // 사용자에게 에러를 보여줄 이유가 없다.
            notificationSettingInsertGateway.insert(created);
        } catch (DataIntegrityViolationException raceCondition) {
            notificationSettingRepository
                    .findByUser_UserIdAndFavoriteProperty_FavoritePropertyId(
                            user.getUserId(), favoriteProperty.getFavoritePropertyId())
                    .orElseThrow(() -> raceCondition)
                    .updateConditions(cmd.priceChangeThresholdPct(), cmd.newTradeAlertYn(), cmd.emailAlertYn());
        }
    }

    private void upsertForRegion(User user, FavoriteRegion favoriteRegion, UpdateNotificationSettingsCommand cmd) {
        Optional<NotificationSetting> existing = notificationSettingRepository
                .findByUser_UserIdAndFavoriteRegion_FavoriteRegionId(
                        user.getUserId(), favoriteRegion.getFavoriteRegionId());
        if (existing.isPresent()) {
            existing.get().updateConditions(cmd.priceChangeThresholdPct(), cmd.newTradeAlertYn(), cmd.emailAlertYn());
            return;
        }

        NotificationSetting created = NotificationSetting.forRegion(
                user, favoriteRegion, cmd.priceChangeThresholdPct(), cmd.newTradeAlertYn(), cmd.emailAlertYn());
        try {
            // upsertForProperty()와 같은 전제·같은 이유 — REQUIRES_NEW로 격리된 INSERT라야 실패해도
            // 이 트랜잭션이 rollback-only로 표시되지 않아 재조회 후 갱신이 안전하다.
            notificationSettingInsertGateway.insert(created);
        } catch (DataIntegrityViolationException raceCondition) {
            notificationSettingRepository
                    .findByUser_UserIdAndFavoriteRegion_FavoriteRegionId(
                            user.getUserId(), favoriteRegion.getFavoriteRegionId())
                    .orElseThrow(() -> raceCondition)
                    .updateConditions(cmd.priceChangeThresholdPct(), cmd.newTradeAlertYn(), cmd.emailAlertYn());
        }
    }
}
