package com.jiseong.homesense.notification.repository;

import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.jiseong.homesense.notification.entity.NotificationSetting;

public interface NotificationSettingRepository extends JpaRepository<NotificationSetting, Long> {

    List<NotificationSetting> findByUser_UserId(Long userId);

    Optional<NotificationSetting> findByUser_UserIdAndFavoriteProperty_FavoritePropertyId(
            Long userId, Long favoritePropertyId);

    Optional<NotificationSetting> findByUser_UserIdAndFavoriteRegion_FavoriteRegionId(
            Long userId, Long favoriteRegionId);

    /** SVC-FAV-01.getFavoriteProperties() — MY-02 카드의 "알림조건 배지" 표시 여부. */
    boolean existsByUser_UserIdAndFavoriteProperty_FavoritePropertyId(Long userId, Long favoritePropertyId);

    /**
     * SVC-FAV-01.getFavoriteProperties() 배치 버전 — existsByUser_UserIdAndFavoriteProperty_FavoritePropertyId()를
     * 관심 매물 개수만큼 반복 호출하는 대신, 알림조건이 걸려 있는 favorite_property_id만 한 번에
     * 모아 반환한다(N+1 제거, Codex 코드리뷰 P2 지적). 호출부가 Set으로 모아 포함 여부만 확인한다.
     */
    @Query("""
            SELECT ns.favoriteProperty.favoritePropertyId
            FROM NotificationSetting ns
            WHERE ns.user.userId = :userId AND ns.favoriteProperty.favoritePropertyId IN :favoritePropertyIds
            """)
    List<Long> findFavoritePropertyIdsWithSetting(@Param("userId") Long userId,
            @Param("favoritePropertyIds") List<Long> favoritePropertyIds);
}
