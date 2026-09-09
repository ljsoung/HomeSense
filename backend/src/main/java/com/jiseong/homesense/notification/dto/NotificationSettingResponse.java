package com.jiseong.homesense.notification.dto;

import java.math.BigDecimal;

import com.jiseong.homesense.notification.entity.NotificationSetting;

/**
 * GET /api/notifications/settings 응답 항목. favoritePropertyId/favoriteRegionId 중 정확히 하나만
 * 채워진다(엔티티의 ck_ntf_setting_target 사상 그대로). 대상의 표시용 이름(단지명/지역 전체경로)은
 * 담지 않는다 — MY-03은 이미 관심 매물·지역 목록(FAV-01)을 화면에 갖고 있어 이 응답과 매물/지역
 * ID로 조인하면 되고, 여기서 다시 조회해 담으면 findByUser_UserId()에 JOIN FETCH를 추가로 얹어야
 * 하는 비용이 생긴다.
 */
public record NotificationSettingResponse(
        Long notificationSettingId,
        Long favoritePropertyId,
        Long favoriteRegionId,
        BigDecimal priceChangeThresholdPct,
        boolean newTradeAlertYn,
        boolean emailAlertYn) {

    public static NotificationSettingResponse from(NotificationSetting setting) {
        return new NotificationSettingResponse(
                setting.getNotificationSettingId(),
                setting.getFavoriteProperty() != null ? setting.getFavoriteProperty().getFavoritePropertyId() : null,
                setting.getFavoriteRegion() != null ? setting.getFavoriteRegion().getFavoriteRegionId() : null,
                setting.getPriceChangeThresholdPct(),
                setting.isNewTradeAlertYn(),
                setting.isEmailAlertYn());
    }
}
