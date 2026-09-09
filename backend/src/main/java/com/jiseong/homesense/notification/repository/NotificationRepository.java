package com.jiseong.homesense.notification.repository;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import com.jiseong.homesense.notification.entity.Notification;
import com.jiseong.homesense.notification.entity.NotificationType;

public interface NotificationRepository extends JpaRepository<Notification, Long> {

    /** SVC-NTF-01.getNotifications() — type 필터 없이 전체 조회(idx_notification_user_read_sent 활용). */
    Page<Notification> findByUser_UserIdOrderBySentAtDesc(Long userId, Pageable pageable);

    Page<Notification> findByUser_UserIdAndNotificationTypeOrderBySentAtDesc(
            Long userId, NotificationType notificationType, Pageable pageable);
}
