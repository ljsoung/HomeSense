package com.jiseong.homesense.notification.dto;

import java.math.BigDecimal;

public record UpdateNotificationSettingsCommand(
        Long favoritePropertyId,
        Long favoriteRegionId,
        BigDecimal priceChangeThresholdPct,
        boolean newTradeAlertYn,
        boolean emailAlertYn) {
}
