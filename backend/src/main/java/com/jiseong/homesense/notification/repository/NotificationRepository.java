package com.jiseong.homesense.notification.repository;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;

import com.jiseong.homesense.notification.entity.Notification;

public interface NotificationRepository extends JpaRepository<Notification, Long> {

    List<Notification> findByUser_UserIdOrderBySentAtDesc(Long userId);
}
