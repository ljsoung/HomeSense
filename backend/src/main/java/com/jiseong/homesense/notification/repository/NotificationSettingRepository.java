package com.jiseong.homesense.notification.repository;

import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;

import com.jiseong.homesense.notification.entity.NotificationSetting;

public interface NotificationSettingRepository extends JpaRepository<NotificationSetting, Long> {

    List<NotificationSetting> findByUser_UserId(Long userId);

    Optional<NotificationSetting> findByUser_UserIdAndFavoriteProperty_FavoritePropertyId(
            Long userId, Long favoritePropertyId);

    Optional<NotificationSetting> findByUser_UserIdAndFavoriteRegion_FavoriteRegionId(
            Long userId, Long favoriteRegionId);
}
