package com.jiseong.homesense.notification.service;

import java.time.LocalDateTime;
import java.util.Comparator;
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
     *
     * <p>같은 회원의 저장은 맨 앞에서 회원 행을 {@code SELECT ... FOR UPDATE}로 잠가 직렬화한다. 대상이 여러 개인 요청 둘이
     * 동시에 오면, 아직 행이 없는 대상의 {@code INSERT ... ON DUPLICATE KEY UPDATE}가 서로의 UNIQUE 인덱스 간격(gap) 잠금을
     * 기다려 교착했다 — 정해진 순서로 보내도({@link #inLockOrder}) 막히지 않았다(Codex 코드리뷰 P2, `NotificationServiceMariaDbIT`로
     * 재현). 설정은 회원 자신만 바꾸므로 직렬화 범위가 한 회원에 한정된다.
     */
    public void updateSettings(Long userId, UpdateNotificationSettingsCommand cmd) {
        userRepository.lockForUpdate(userId);
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
        for (UpdateNotificationSettingsCommand.Item item : inLockOrder(cmd.settings())) {
            notificationSettingRepository.upsert(userId, item.favoritePropertyId(), item.favoriteRegionId(),
                    item.priceChangeThresholdPct(), item.newTradeAlertYn(), item.emailAlertYn(), now);
        }
    }

    /**
     * upsert를 보낼 순서 — 관심 매물 먼저 id 오름차순, 그다음 관심 지역 id 오름차순.
     *
     * <p>upsert는 대상 행(UNIQUE 인덱스 항목)에 잠금을 걸고 트랜잭션이 끝날 때까지 쥔다. 겹치는 대상을 서로 다른 순서로 담은
     * 두 요청이 동시에 오면(두 탭에서 [매물 1, 매물 2]와 [매물 2, 매물 1]) 각자 첫 행을 잠근 채 상대의 행을 기다려 교착하고,
     * MariaDB가 한쪽을 롤백시킨다(Codex 코드리뷰 P2). 모든 요청이 같은 순서로 잠그면 기존 행끼리는 이 순환 대기가 생기지 않는다.
     * 같은 대상 중복은 앞에서 이미 막았으므로 키가 겹치지 않는다. 새 행 INSERT의 간격 잠금 교착은 순서로 막히지 않아
     * {@link #updateSettings}가 회원 행 잠금으로 막는다 — 이 정렬은 그 잠금을 다른 경로로 바꾸게 될 때를 위한 보조 장치다.
     */
    private static List<UpdateNotificationSettingsCommand.Item> inLockOrder(List<UpdateNotificationSettingsCommand.Item> items) {
        return items.stream()
                .sorted(Comparator
                        .comparing((UpdateNotificationSettingsCommand.Item item) -> item.favoritePropertyId() == null)
                        .thenComparing(item -> item.favoritePropertyId() != null
                                ? item.favoritePropertyId() : item.favoriteRegionId()))
                .toList();
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
