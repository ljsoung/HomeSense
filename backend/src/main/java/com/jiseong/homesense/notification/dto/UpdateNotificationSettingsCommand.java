package com.jiseong.homesense.notification.dto;

import java.math.BigDecimal;
import java.util.List;

/** SVC-NTF-01.updateSettings() 입력 — 대상별 설정 목록. 목록 전체가 한 트랜잭션으로 저장된다. */
public record UpdateNotificationSettingsCommand(List<Item> settings) {

    public record Item(
            Long favoritePropertyId,
            Long favoriteRegionId,
            BigDecimal priceChangeThresholdPct,
            boolean newTradeAlertYn,
            boolean emailAlertYn) {
    }
}
