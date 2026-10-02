package com.jiseong.homesense.favorite.dto;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

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
 * <p>recent*(최근 거래가·거래일·전용면적·층)는 취소되지 않은 <b>매매(SALE)</b> 거래 중 최신 1건에서 채운다
 * (TradeRepository#findRecentSaleTradesByComplexIds, 2026-10-02 MY-02 착수 시 변경 — 그 전에는 매매/전월세를
 * 가리지 않아 전세 보증금이 "최근 거래가"로 보일 수 있었다). changeRate(최근 1개월 매매 평균 vs 그 직전
 * 1개월 매매 평균)도 매매만 대상이라 두 값의 기준이 같다. 매매 거래가 없는 단지는 recent*가 모두 null이다.
 * recentDealCategory는 이제 항상 SALE 또는 null이지만 기존 소비자(MY-01 미리보기) 호환을 위해 남긴다.
 * 면적·층은 같은 단지라도 평형별 가격이 크게 달라 최근 거래가를 해석하는 데 필요하다(MY-02 카드 "84㎡ · 9층").
 */
public record FavoritePropertySummaryResponse(
        Long favoritePropertyId,
        Long complexId,
        String complexName,
        String sido,
        String sigungu,
        String dongRi,
        HousingType housingType,
        LocalDateTime registeredAt,
        DealCategory recentDealCategory,
        LocalDate recentDealDate,
        Long recentAmount,
        BigDecimal recentArea,
        Short recentFloor,
        BigDecimal changeRate,
        boolean hasNotificationSetting) {

    public static FavoritePropertySummaryResponse of(FavoriteProperty favorite, Trade recentTrade,
            BigDecimal changeRate, boolean hasNotificationSetting) {
        Complex complex = favorite.getComplex();

        return new FavoritePropertySummaryResponse(
                favorite.getFavoritePropertyId(),
                complex.getComplexId(),
                complex.getComplexName(),
                complex.getSido(),
                complex.getSigungu(),
                complex.getDongRi(),
                favorite.getHousingType(),
                favorite.getRegisteredAt(),
                recentTrade == null ? null : recentTrade.getDealCategory(),
                recentTrade == null ? null : recentTrade.getDealDate(),
                recentTrade == null ? null : recentTrade.getDealAmount(),
                recentTrade == null ? null : recentTrade.getExcluUseArea(),
                recentTrade == null ? null : recentTrade.getFloor(),
                changeRate,
                hasNotificationSetting);
    }
}
