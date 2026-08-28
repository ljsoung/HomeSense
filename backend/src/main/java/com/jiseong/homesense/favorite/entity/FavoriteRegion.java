package com.jiseong.homesense.favorite.entity;

import java.time.LocalDateTime;

import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

import com.jiseong.homesense.region.entity.LegalDistrictCode;
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
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Entity
@Table(name = "favorite_region",
        uniqueConstraints = @UniqueConstraint(name = "uk_fav_region_user_dong", columnNames = {"user_id", "legal_dong_cd"}))
@EntityListeners(AuditingEntityListener.class)
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class FavoriteRegion {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "favorite_region_id")
    private Long favoriteRegionId;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "legal_dong_cd", nullable = false)
    private LegalDistrictCode legalDistrictCode;

    @Column(name = "registered_at", nullable = false)
    private LocalDateTime registeredAt;

    @CreatedDate
    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @Builder
    private FavoriteRegion(User user, LegalDistrictCode legalDistrictCode, LocalDateTime registeredAt) {
        this.user = user;
        this.legalDistrictCode = legalDistrictCode;
        this.registeredAt = registeredAt;
    }

    public static FavoriteRegion register(User user, LegalDistrictCode legalDistrictCode) {
        return FavoriteRegion.builder()
                .user(user)
                .legalDistrictCode(legalDistrictCode)
                .registeredAt(LocalDateTime.now())
                .build();
    }
}
