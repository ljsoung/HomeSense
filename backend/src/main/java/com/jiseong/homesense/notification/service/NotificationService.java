package com.jiseong.homesense.notification.service;

import java.time.LocalDateTime;
import java.util.List;

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
import com.jiseong.homesense.notification.entity.NotificationType;
import com.jiseong.homesense.notification.exception.AccessDeniedException;
import com.jiseong.homesense.notification.exception.InvalidNotificationTargetException;
import com.jiseong.homesense.notification.exception.MissingTargetException;
import com.jiseong.homesense.notification.exception.NotificationNotFoundException;
import com.jiseong.homesense.notification.repository.NotificationRepository;
import com.jiseong.homesense.notification.repository.NotificationSettingRepository;

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

    @Transactional(readOnly = true)
    public List<NotificationSettingResponse> getSettings(Long userId) {
        return notificationSettingRepository.findByUser_UserId(userId).stream()
                .map(NotificationSettingResponse::from)
                .toList();
    }

    /**
     * favoritePropertyId/favoriteRegionId 중 정확히 하나가 가리키는 대상(반드시 본인 소유)에 대해
     * 원자적 upsert(존재하면 UPDATE, 없으면 INSERT)를 수행한다.
     *
     * <p>애초에 "조회 → 있으면 UPDATE, 없으면 INSERT"를 애플리케이션 레벨에서 분기하고, 동시 INSERT
     * 경쟁으로 인한 UNIQUE 위반은 별도 REQUIRES_NEW 트랜잭션(TradeInsertGateway와 같은 패턴)으로
     * 격리해 처리하도록 구현했었다. 하지만 REQUIRES_NEW로 그 트랜잭션의 rollback-only 문제를 피하더라도,
     * MariaDB 기본 격리수준(REPEATABLE READ)에서는 실패 이후 같은(바깥) 트랜잭션에서의 재조회가 그
     * 트랜잭션이 이미 확립한 스냅샷에 묶여 경쟁에서 이긴 다른 트랜잭션의 커밋을 여전히 보지 못한다 —
     * 재조회가 다시 empty를 반환해 재시도가 실패하고 예외가 그대로 전파된다(Codex 코드리뷰 P1 지적,
     * 새로 추가한 NotificationServiceMariaDbIT가 정확히 이 순서를 재현한다). {@link
     * NotificationSettingRepository#upsert}(네이티브 {@code INSERT ... ON DUPLICATE KEY UPDATE})는
     * 단일 원자적 SQL 문장이라 이 스냅샷 문제 자체가 발생하지 않는다 — 조회·재시도·트랜잭션 격리가
     * 전혀 필요 없다.
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

        if (hasProperty) {
            FavoriteProperty favoriteProperty = favoritePropertyRepository.findById(cmd.favoritePropertyId())
                    .orElseThrow(FavoriteNotFoundException::new);
            if (!favoriteProperty.getUser().getUserId().equals(userId)) {
                throw new AccessDeniedException();
            }
        } else {
            FavoriteRegion favoriteRegion = favoriteRegionRepository.findById(cmd.favoriteRegionId())
                    .orElseThrow(FavoriteNotFoundException::new);
            if (!favoriteRegion.getUser().getUserId().equals(userId)) {
                throw new AccessDeniedException();
            }
        }

        notificationSettingRepository.upsert(userId, cmd.favoritePropertyId(), cmd.favoriteRegionId(),
                cmd.priceChangeThresholdPct(), cmd.newTradeAlertYn(), cmd.emailAlertYn(), LocalDateTime.now());
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
}
