package com.jiseong.homesense.notification.dto;

import java.time.LocalDateTime;

import com.jiseong.homesense.notification.entity.Notification;
import com.jiseong.homesense.notification.entity.NotificationType;

/**
 * GET /api/notifications 응답 항목(MY-04). complexId/legalDongCd/tradeId는 알림 유형에 맞는 딥링크
 * 대상만 선택적으로 채워지며 상호 배타가 아니다(Notification 엔티티 주석 참고 — 신규거래 알림은
 * complexId와 tradeId가 함께 채워질 수 있다). 세 연관관계 모두 LAZY지만 식별자만 읽으므로(프록시가
 * FK 값으로 이미 알고 있는 식별자) 추가 JOIN FETCH 없이도 N+1이 발생하지 않는다.
 */
public record NotificationResponse(
        Long notificationId,
        NotificationType notificationType,
        String title,
        String message,
        Long complexId,
        String legalDongCd,
        Long tradeId,
        boolean isRead,
        LocalDateTime sentAt) {

    public static NotificationResponse from(Notification notification) {
        return new NotificationResponse(
                notification.getNotificationId(),
                notification.getNotificationType(),
                notification.getTitle(),
                notification.getMessage(),
                notification.getComplex() != null ? notification.getComplex().getComplexId() : null,
                notification.getLegalDistrictCode() != null
                        ? notification.getLegalDistrictCode().getLegalDongCd() : null,
                notification.getTrade() != null ? notification.getTrade().getTradeId() : null,
                notification.isRead(),
                notification.getSentAt());
    }
}
