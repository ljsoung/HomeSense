package com.jiseong.homesense.notification.entity;

import java.time.LocalDateTime;

import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

import com.jiseong.homesense.complex.entity.Complex;
import com.jiseong.homesense.region.entity.LegalDistrictCode;
import com.jiseong.homesense.trade.entity.Trade;
import com.jiseong.homesense.user.entity.User;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EntityListeners;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Entity
@Table(name = "notification")
@EntityListeners(AuditingEntityListener.class)
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Notification {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "notification_id")
    private Long notificationId;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    @Enumerated(EnumType.STRING)
    @Column(name = "notification_type", nullable = false, length = 15)
    private NotificationType notificationType;

    @Column(name = "title", nullable = false, length = 200)
    private String title;

    @Column(name = "message", length = 500)
    private String message;

    /**
     * complex/legalDistrictCode/trade는 알림 유형에 맞는 딥링크 대상만 선택적으로 채워지며
     * 상호 배타가 아니다(예: 신규거래 알림은 trade와 complex가 동시에 채워질 수 있다).
     */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "complex_id", nullable = true)
    private Complex complex;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "legal_dong_cd", nullable = true)
    private LegalDistrictCode legalDistrictCode;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "trade_id", nullable = true)
    private Trade trade;

    @Column(name = "is_read", nullable = false)
    private boolean isRead;

    /**
     * 발송 완료 시각. BAT-NTF-01은 알림을 만들 때 비워 두고, BAT-MAIL-01이 발송한 뒤 채운다 —
     * {@code sent_at IS NULL}이 곧 발송 대기열이다(CLAUDE.md "BAT-NTF-01 구현 결정 사항" D1).
     * 목록 정렬·표시 시각은 이 값이 아니라 {@link #createdAt}을 쓴다.
     */
    @Column(name = "sent_at")
    private LocalDateTime sentAt;

    @CreatedDate
    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @Builder
    private Notification(User user, NotificationType notificationType, String title, String message,
                          Complex complex, LegalDistrictCode legalDistrictCode, Trade trade) {
        this.user = user;
        this.notificationType = notificationType;
        this.title = title;
        this.message = message;
        this.complex = complex;
        this.legalDistrictCode = legalDistrictCode;
        this.trade = trade;
        this.isRead = false;
    }

    public void markAsRead() {
        this.isRead = true;
    }
}
