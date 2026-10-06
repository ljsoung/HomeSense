package com.jiseong.homesense.notification.repository;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import com.jiseong.homesense.notification.entity.Notification;
import com.jiseong.homesense.notification.entity.NotificationType;

public interface NotificationRepository extends JpaRepository<Notification, Long> {

    /**
     * SVC-NTF-01.getNotifications() — 발생 시각(created_at) 최신순, 같은 초면 ID 역순. sent_at은 발송 전이면
     * NULL이라 정렬 기준이 될 수 없고, 미발송 알림도 목록에 보여야 한다(BAT-NTF-01 D1).
     * idx_notification_user_read_sent(user_id, is_read, sent_at)는 user_id 범위 조회에만 쓰이고 정렬은
     * filesort다 — sent_at 정렬이던 때도 is_read가 끼어 있어 마찬가지였다.
     */
    Page<Notification> findByUser_UserIdOrderByCreatedAtDescNotificationIdDesc(Long userId, Pageable pageable);

    Page<Notification> findByUser_UserIdAndNotificationTypeOrderByCreatedAtDescNotificationIdDesc(
            Long userId, NotificationType notificationType, Pageable pageable);
}
