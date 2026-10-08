package com.jiseong.homesense.notification.dto;

import java.math.BigDecimal;
import java.util.List;

import jakarta.validation.Valid;
import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/**
 * MY-03 알림 조건 설정 요청 — 선택한 대상 여러 곳을 한 번에 저장한다(2026-10-07, MY-03 다중 선택 일괄 저장).
 * 대상 하나만 받던 형태에서 바꿨다: 프론트가 대상마다 PUT을 반복하면 중간 실패 시 일부만 저장되고 재시도 범위도 모호해진다.
 * 목록 전체를 한 트랜잭션으로 처리한다(SVC-NTF-01.updateSettings).
 *
 * <p>목록은 비면 안 되고 200건까지다 — 관심 매물·지역 수에 상한은 없지만 한 화면에서 고르는 대상이라 그 이상은 비정상 요청으로 본다.
 */
public record UpdateNotificationSettingsRequest(
        @NotEmpty @Size(max = 200) List<@NotNull @Valid Item> settings) {

    /**
     * 대상 하나의 설정. favoritePropertyId/favoriteRegionId에는 {@code @NotNull}을 걸지 않는다 — "정확히 하나만"/"둘 다
     * 없음"을 Service가 각각 {@code InvalidNotificationTargetException}/{@code MissingTargetException}으로 구분해 던지도록
     * 설계서가 지정하고 있어서다(AddFavoritePropertyRequest.complexId와 같은 이유).
     *
     * <p>priceChangeThresholdPct는 0 이상 20 이하의 정수다(MY-03 슬라이더 0~20, 1단위). 0%는 "조금이라도 변동되면 알림"으로
     * 동작하는 정상 값이다(BAT-NTF-01은 반올림 변동률이 0.0이면 보내지 않는다). {@code @Digits(fraction = 0)}이 소수를 막는다 —
     * 컬럼은 DECIMAL(4,1)이지만 이 화면의 계약은 정수다(CLAUDE.md "DECIMAL 컬럼 @Digits" 원칙).
     * 두 Boolean은 래퍼 + {@code @NotNull}이다 — 원시 boolean이면 키가 빠진 요청이 조용히 false로 저장된다.
     */
    public record Item(
            Long favoritePropertyId,
            Long favoriteRegionId,
            @NotNull @DecimalMin("0") @DecimalMax("20") @Digits(integer = 2, fraction = 0)
            BigDecimal priceChangeThresholdPct,
            @NotNull Boolean newTradeAlertYn,
            @NotNull Boolean emailAlertYn) {
    }

    public UpdateNotificationSettingsCommand toCommand() {
        return new UpdateNotificationSettingsCommand(settings.stream()
                .map(item -> new UpdateNotificationSettingsCommand.Item(item.favoritePropertyId(), item.favoriteRegionId(),
                        item.priceChangeThresholdPct(), item.newTradeAlertYn(), item.emailAlertYn()))
                .toList());
    }
}
