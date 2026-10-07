package com.jiseong.homesense.notification.service;

import java.time.LocalDateTime;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

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
import com.jiseong.homesense.notification.exception.DuplicateNotificationTargetException;
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
     * 대상별 설정 목록을 한 트랜잭션으로 원자적 upsert한다(존재하면 UPDATE, 없으면 INSERT). 각 항목은
     * favoritePropertyId/favoriteRegionId 중 정확히 하나를 가리켜야 하고, 그 대상은 반드시 본인 소유여야 한다.
     *
     * <p>모든 항목을 먼저 검증한 뒤에 저장한다 — 대상 지정 오류(400), 같은 대상 중복(400), 없는 대상(404), 남의 대상(403) 중
     * 하나라도 있으면 아무것도 저장하지 않는다. 예외는 모두 {@code BusinessException}(RuntimeException)이라 이미 저장한
     * 항목이 있었더라도 트랜잭션 롤백으로 함께 취소된다(검증을 먼저 하는 것은 불필요한 쓰기를 피하려는 것이다).
     * 소유권 확인은 매물·지역 각각 {@code findAllById} 한 번으로 묶는다(항목 수만큼 조회하지 않는다).
     *
     * <p>저장은 {@link NotificationSettingRepository#upsert}(네이티브 {@code INSERT ... ON DUPLICATE KEY UPDATE})다.
     * 애플리케이션에서 "조회 → 있으면 UPDATE, 없으면 INSERT"로 분기하면, REQUIRES_NEW로 INSERT를 격리해도 MariaDB 기본
     * 격리수준(REPEATABLE READ)의 스냅샷 때문에 실패 뒤 재조회가 경쟁에서 이긴 커밋을 보지 못했다(Codex 코드리뷰 P1, CLAUDE.md
     * SVC-NTF-01 절). 단일 원자적 문장이라 같은 대상을 동시에 저장해도(더블클릭·두 탭) UNIQUE 위반이 나지 않는다.
     */
    public void updateSettings(Long userId, UpdateNotificationSettingsCommand cmd) {
        Set<Long> propertyIds = new LinkedHashSet<>();
        Set<Long> regionIds = new LinkedHashSet<>();
        for (UpdateNotificationSettingsCommand.Item item : cmd.settings()) {
            boolean hasProperty = item.favoritePropertyId() != null;
            boolean hasRegion = item.favoriteRegionId() != null;
            if (hasProperty && hasRegion) {
                throw new InvalidNotificationTargetException();
            }
            if (!hasProperty && !hasRegion) {
                throw new MissingTargetException();
            }
            boolean added = hasProperty ? propertyIds.add(item.favoritePropertyId()) : regionIds.add(item.favoriteRegionId());
            if (!added) {
                throw new DuplicateNotificationTargetException();
            }
        }

        verifyOwnership(userId, propertyIds, favoritePropertyRepository.findAllById(propertyIds).stream()
                .collect(Collectors.toMap(FavoriteProperty::getFavoritePropertyId, p -> p.getUser().getUserId())));
        verifyOwnership(userId, regionIds, favoriteRegionRepository.findAllById(regionIds).stream()
                .collect(Collectors.toMap(FavoriteRegion::getFavoriteRegionId, r -> r.getUser().getUserId())));

        LocalDateTime now = LocalDateTime.now();
        for (UpdateNotificationSettingsCommand.Item item : cmd.settings()) {
            notificationSettingRepository.upsert(userId, item.favoritePropertyId(), item.favoriteRegionId(),
                    item.priceChangeThresholdPct(), item.newTradeAlertYn(), item.emailAlertYn(), now);
        }
    }

    /** 요청한 대상이 하나라도 없으면 404, 남의 것이면 403. ownerById는 찾은 대상의 id → 소유자 userId. */
    private static void verifyOwnership(Long userId, Set<Long> requestedIds, Map<Long, Long> ownerById) {
        for (Long id : requestedIds) {
            Long ownerId = ownerById.get(id);
            if (ownerId == null) {
                throw new FavoriteNotFoundException();
            }
            if (!ownerId.equals(userId)) {
                throw new AccessDeniedException();
            }
        }
    }

    @Transactional(readOnly = true)
    public Page<NotificationResponse> getNotifications(Long userId, NotificationType typeFilter, Pageable pageable) {
        Page<Notification> notifications = typeFilter != null
                ? notificationRepository.findByUser_UserIdAndNotificationTypeOrderByCreatedAtDescNotificationIdDesc(
                        userId, typeFilter, pageable)
                : notificationRepository.findByUser_UserIdOrderByCreatedAtDescNotificationIdDesc(userId, pageable);
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
