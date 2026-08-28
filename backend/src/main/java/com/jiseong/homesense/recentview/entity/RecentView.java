package com.jiseong.homesense.recentview.entity;

import java.time.LocalDateTime;

import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

import com.jiseong.homesense.complex.entity.Complex;
import com.jiseong.homesense.trade.entity.HousingType;
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
import lombok.Getter;
import lombok.NoArgsConstructor;

@Entity
@Table(name = "recent_view")
@EntityListeners(AuditingEntityListener.class)
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class RecentView {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "recent_view_id")
    private Long recentViewId;

    /**
     * 비로그인 조회는 NULL. user/sessionId 중 최소 하나는 항상 채워진다(ck_recent_view_actor).
     */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = true)
    private User user;

    @Column(name = "session_id", length = 100)
    private String sessionId;

    @Enumerated(EnumType.STRING)
    @Column(name = "housing_type", nullable = false, length = 10)
    private HousingType housingType;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "complex_id", nullable = false)
    private Complex complex;

    @Column(name = "viewed_at", nullable = false)
    private LocalDateTime viewedAt;

    @CreatedDate
    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    private RecentView(User user, String sessionId, HousingType housingType, Complex complex, LocalDateTime viewedAt) {
        if (user == null && sessionId == null) {
            throw new IllegalArgumentException("user 또는 sessionId 중 최소 하나는 필요합니다.");
        }
        this.user = user;
        this.sessionId = sessionId;
        this.housingType = housingType;
        this.complex = complex;
        this.viewedAt = viewedAt;
    }

    public static RecentView record(User user, String sessionId, Complex complex, HousingType housingType) {
        return new RecentView(user, sessionId, housingType, complex, LocalDateTime.now());
    }
}
