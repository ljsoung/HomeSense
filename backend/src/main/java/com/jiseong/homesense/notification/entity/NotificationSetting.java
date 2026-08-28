package com.jiseong.homesense.notification.entity;

import java.math.BigDecimal;
import java.time.LocalDateTime;

import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.annotation.LastModifiedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

import com.jiseong.homesense.favorite.entity.FavoriteProperty;
import com.jiseong.homesense.favorite.entity.FavoriteRegion;
import com.jiseong.homesense.user.entity.User;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EntityListeners;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Entity
@Table(name = "notification_setting", uniqueConstraints = {
        @UniqueConstraint(name = "uk_ntf_setting_user_property", columnNames = {"user_id", "favorite_property_id"}),
        @UniqueConstraint(name = "uk_ntf_setting_user_region", columnNames = {"user_id", "favorite_region_id"})
})
@EntityListeners(AuditingEntityListener.class)
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class NotificationSetting {

    public static final BigDecimal DEFAULT_PRICE_CHANGE_THRESHOLD_PCT = new BigDecimal("5.0");

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "notification_setting_id")
    private Long notificationSettingId;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    /**
     * favoriteProperty/favoriteRegion 중 정확히 하나만 채워진다(ck_ntf_setting_target).
     */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "favorite_property_id", nullable = true)
    private FavoriteProperty favoriteProperty;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "favorite_region_id", nullable = true)
    private FavoriteRegion favoriteRegion;

    @Column(name = "price_change_threshold_pct", nullable = false, precision = 4, scale = 1)
    private BigDecimal priceChangeThresholdPct;

    @Column(name = "new_trade_alert_yn", nullable = false)
    private boolean newTradeAlertYn;

    @Column(name = "email_alert_yn", nullable = false)
    private boolean emailAlertYn;

    @CreatedDate
    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @LastModifiedDate
    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;

    private NotificationSetting(User user, FavoriteProperty favoriteProperty, FavoriteRegion favoriteRegion,
                                 BigDecimal priceChangeThresholdPct, boolean newTradeAlertYn, boolean emailAlertYn) {
        boolean hasProperty = favoriteProperty != null;
        boolean hasRegion = favoriteRegion != null;
        if (hasProperty == hasRegion) {
            throw new IllegalArgumentException("favoriteProperty와 favoriteRegion 중 정확히 하나만 지정해야 합니다.");
        }
        this.user = user;
        this.favoriteProperty = favoriteProperty;
        this.favoriteRegion = favoriteRegion;
        this.priceChangeThresholdPct = priceChangeThresholdPct;
        this.newTradeAlertYn = newTradeAlertYn;
        this.emailAlertYn = emailAlertYn;
    }

    public static NotificationSetting forProperty(User user, FavoriteProperty favoriteProperty,
                                                   BigDecimal priceChangeThresholdPct,
                                                   boolean newTradeAlertYn, boolean emailAlertYn) {
        return new NotificationSetting(user, favoriteProperty, null, priceChangeThresholdPct, newTradeAlertYn, emailAlertYn);
    }

    public static NotificationSetting forRegion(User user, FavoriteRegion favoriteRegion,
                                                 BigDecimal priceChangeThresholdPct,
                                                 boolean newTradeAlertYn, boolean emailAlertYn) {
        return new NotificationSetting(user, null, favoriteRegion, priceChangeThresholdPct, newTradeAlertYn, emailAlertYn);
    }

    public void updateConditions(BigDecimal priceChangeThresholdPct, boolean newTradeAlertYn, boolean emailAlertYn) {
        this.priceChangeThresholdPct = priceChangeThresholdPct;
        this.newTradeAlertYn = newTradeAlertYn;
        this.emailAlertYn = emailAlertYn;
    }
}
