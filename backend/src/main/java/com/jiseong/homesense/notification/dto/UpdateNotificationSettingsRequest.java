package com.jiseong.homesense.notification.dto;

import java.math.BigDecimal;

import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotNull;

/**
 * MY-03 알림 조건 설정(대상당 1건 upsert) 요청. favoritePropertyId/favoriteRegionId에는
 * {@code @NotNull}을 걸지 않는다 — "정확히 하나만"/"둘 다 없음"을 Service가 각각
 * {@code InvalidNotificationTargetException}/{@code MissingTargetException}으로 구분해 던지도록
 * 설계서가 지정하고 있어, Bean Validation으로 옮기면 COM-VAL-01의 범용 VALIDATION_FAILED 응답으로
 * 뭉뚱그려져 그 지정을 어기게 된다(AddFavoritePropertyRequest.complexId와 같은 이유).
 *
 * <p>priceChangeThresholdPct의 0~100 범위 검증은 Service 처리 로직이 지정한 대상이 아니라
 * 설계서 예외표에도 전용 예외가 없어 COM-VAL-01 표준 경로(Bean Validation)를 그대로 쓴다 — 0%는
 * 하한 그대로 허용되는 값이라(MY-03 예외 처리표) {@code @DecimalMin}의 기본 inclusive=true가
 * 정확히 이 요구와 일치한다.
 */
public record UpdateNotificationSettingsRequest(
        Long favoritePropertyId,
        Long favoriteRegionId,
        @NotNull @DecimalMin("0.0") @DecimalMax("100.0") BigDecimal priceChangeThresholdPct,
        boolean newTradeAlertYn,
        boolean emailAlertYn) {

    public UpdateNotificationSettingsCommand toCommand() {
        return new UpdateNotificationSettingsCommand(
                favoritePropertyId, favoriteRegionId, priceChangeThresholdPct, newTradeAlertYn, emailAlertYn);
    }
}
