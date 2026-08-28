package com.jiseong.homesense.favorite.entity;

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
import jakarta.persistence.UniqueConstraint;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Entity
@Table(name = "favorite_property",
        uniqueConstraints = @UniqueConstraint(name = "uk_fav_prop_user_complex", columnNames = {"user_id", "complex_id"}))
@EntityListeners(AuditingEntityListener.class)
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class FavoriteProperty {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "favorite_property_id")
    private Long favoritePropertyId;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    @Enumerated(EnumType.STRING)
    @Column(name = "housing_type", nullable = false, length = 10)
    private HousingType housingType;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "complex_id", nullable = false)
    private Complex complex;

    @Column(name = "registered_at", nullable = false)
    private LocalDateTime registeredAt;

    @CreatedDate
    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @Builder
    private FavoriteProperty(User user, HousingType housingType, Complex complex, LocalDateTime registeredAt) {
        this.user = user;
        this.housingType = housingType;
        this.complex = complex;
        this.registeredAt = registeredAt;
    }

    public static FavoriteProperty register(User user, Complex complex, HousingType housingType) {
        return FavoriteProperty.builder()
                .user(user)
                .complex(complex)
                .housingType(housingType)
                .registeredAt(LocalDateTime.now())
                .build();
    }
}
