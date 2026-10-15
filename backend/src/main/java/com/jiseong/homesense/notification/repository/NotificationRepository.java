package com.jiseong.homesense.notification.repository;

import java.time.LocalDateTime;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import com.jiseong.homesense.notification.entity.Notification;
import com.jiseong.homesense.notification.entity.NotificationType;

public interface NotificationRepository extends JpaRepository<Notification, Long> {

    /**
     * SVC-NTF-01.getNotifications() — 조회 기간 창(created_at >= since) 안의 알림을 발생 시각(created_at) 최신순, 같으면 ID
     * 역순으로. 같은 배치 런의 알림은 created_at이 같은 경우가 많아 ID 보조 정렬이 없으면 페이지 경계에서 중복·누락이 생긴다.
     * sent_at은 발송 전이면 NULL이라 정렬 기준이 될 수 없고, 미발송 알림도 목록에 보여야 한다(BAT-NTF-01 D1).
     * 호출하는 쪽은 정렬 없는 Pageable을 넘긴다 — 정렬이 실리면 이 정렬 뒤에 덧붙는다.
     * idx_notification_user_read_sent(user_id, is_read, sent_at)는 user_id 범위 조회에만 쓰이고 정렬은 filesort다
     * (인덱스를 추가하지 않았다 — CLAUDE.md "SCR-MY-04" 절 D5).
     */
    Page<Notification> findByUser_UserIdAndCreatedAtGreaterThanEqualOrderByCreatedAtDescNotificationIdDesc(
            Long userId, LocalDateTime since, Pageable pageable);

    Page<Notification> findByUser_UserIdAndNotificationTypeAndCreatedAtGreaterThanEqualOrderByCreatedAtDescNotificationIdDesc(
            Long userId, NotificationType notificationType, LocalDateTime since, Pageable pageable);
}
