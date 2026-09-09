package com.jiseong.homesense.notification.repository;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.jiseong.homesense.notification.entity.NotificationSetting;

public interface NotificationSettingRepository extends JpaRepository<NotificationSetting, Long> {

    List<NotificationSetting> findByUser_UserId(Long userId);

    /**
     * SVC-NTF-01.updateSettings() 전용 원자적 upsert. favoritePropertyId/favoriteRegionId 중 정확히
     * 하나만 채워 호출한다(다른 하나는 null) — {@code uk_ntf_setting_user_property}/
     * {@code uk_ntf_setting_user_region} 중 실제로 걸리는 UNIQUE 인덱스 하나만 충돌을 판단한다.
     *
     * <p>애초에 "조회 → 있으면 UPDATE, 없으면 INSERT" 애플리케이션 레벨 분기 대신 이 방식을 쓴 이유:
     * REQUIRES_NEW로 INSERT만 격리해 rollback-only 문제를 피하더라도, MariaDB 기본 격리수준
     * (REPEATABLE READ)에서는 그 INSERT 실패 후 같은(바깥) 트랜잭션에서 재조회해도 그 트랜잭션이 이미
     * 확립한 스냅샷 때문에 경쟁에서 이긴 다른 트랜잭션의 커밋을 여전히 보지 못한다 — 재조회가 다시
     * empty를 반환해 재시도가 실패하고 예외가 그대로 전파된다(Codex 코드리뷰 P1 지적, 최초 구현이
     * 정확히 이 함정이었다: NotificationSettingInsertGateway로 REQUIRES_NEW 격리까지는 했지만 재조회
     * 자체가 스냅샷에 묶여 있었다). {@code INSERT ... ON DUPLICATE KEY UPDATE}는 단일 원자적 문장이라
     * 이 스냅샷 문제 자체가 발생하지 않는다 — 별도 조회·재시도·REQUIRES_NEW 격리가 전혀 필요 없다.
     */
    @Modifying
    @Query(value = """
            INSERT INTO notification_setting
                (user_id, favorite_property_id, favorite_region_id, price_change_threshold_pct,
                 new_trade_alert_yn, email_alert_yn, created_at, updated_at)
            VALUES (:userId, :favoritePropertyId, :favoriteRegionId, :priceChangeThresholdPct,
                    :newTradeAlertYn, :emailAlertYn, :now, :now)
            ON DUPLICATE KEY UPDATE
                price_change_threshold_pct = VALUES(price_change_threshold_pct),
                new_trade_alert_yn = VALUES(new_trade_alert_yn),
                email_alert_yn = VALUES(email_alert_yn),
                updated_at = VALUES(updated_at)
            """, nativeQuery = true)
    void upsert(@Param("userId") Long userId, @Param("favoritePropertyId") Long favoritePropertyId,
            @Param("favoriteRegionId") Long favoriteRegionId,
            @Param("priceChangeThresholdPct") BigDecimal priceChangeThresholdPct,
            @Param("newTradeAlertYn") boolean newTradeAlertYn, @Param("emailAlertYn") boolean emailAlertYn,
            @Param("now") LocalDateTime now);

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
