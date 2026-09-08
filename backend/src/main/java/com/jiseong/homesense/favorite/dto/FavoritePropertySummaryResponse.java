package com.jiseong.homesense.favorite.dto;

import java.math.BigDecimal;
import java.time.LocalDate;

import com.jiseong.homesense.complex.entity.Complex;
import com.jiseong.homesense.favorite.entity.FavoriteProperty;
import com.jiseong.homesense.trade.entity.DealCategory;
import com.jiseong.homesense.trade.entity.HousingType;
import com.jiseong.homesense.trade.entity.Trade;

/**
 * MY-02 관심 매물 리스트 카드 한 건. GET /api/complexes/popular(ComplexSummaryResponse)와 달리 이
 * 목록은 대표 거래가 없는 항목(신규 매칭 단지 등)도 절대 누락하지 않는다 — 사용자가 명시적으로
 * 등록한 대상이라 데이터가 없다고 목록에서 조용히 빼면 안 되므로, recentAmount 등은 null로 두고
 * "데이터 없음"을 프론트가 표시하게 한다(ComplexService.buildSummary()가 대표 거래 없는 후보를
 * 걸러내는 것과 반대 방향의 판단).
 *
 * <p>recentAmount(최근 거래가)는 "가장 최근 거래" 1건을 매매/전월세 구분 없이 그대로 쓰지만(SVC-CPX-01
 * ComplexSummaryResponse와 같은 관례), changeRate(전월 대비 변동률)는 매매(SALE)만 대상으로 한다
 * (SVC-CPX-01/TRD-01/RGN-01의 기존 "금액 구조가 달라 하나로 합산할 기준이 없다" 결정과 같은 이유) —
 * 즉 최근 거래가 전세/월세였다면 changeRate가 null일 수 있다(지성 확인 필요, 설계서가 다루지 않은
 * 간극).
 */
public record FavoritePropertySummaryResponse(
        Long favoritePropertyId,
        Long complexId,
        String complexName,
        String sido,
        String sigungu,
        String dongRi,
        HousingType housingType,
        DealCategory recentDealCategory,
        LocalDate recentDealDate,
        Long recentAmount,
        BigDecimal changeRate,
        boolean hasNotificationSetting) {

    public static FavoritePropertySummaryResponse of(FavoriteProperty favorite, Trade recentTrade,
            BigDecimal changeRate, boolean hasNotificationSetting) {
        Complex complex = favorite.getComplex();
        Long recentAmount = recentTrade == null ? null
                : recentTrade.getDealCategory() == DealCategory.SALE
                        ? recentTrade.getDealAmount()
                        : recentTrade.getDepositAmount();

        return new FavoritePropertySummaryResponse(
                favorite.getFavoritePropertyId(),
                complex.getComplexId(),
                complex.getComplexName(),
                complex.getSido(),
                complex.getSigungu(),
                complex.getDongRi(),
                favorite.getHousingType(),
                recentTrade == null ? null : recentTrade.getDealCategory(),
                recentTrade == null ? null : recentTrade.getDealDate(),
                recentAmount,
                changeRate,
                hasNotificationSetting);
    }
}
