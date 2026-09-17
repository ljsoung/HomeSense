package com.jiseong.homesense.recentview.dto;

import java.math.BigDecimal;
import java.time.LocalDateTime;

import com.jiseong.homesense.recentview.entity.RecentView;
import com.jiseong.homesense.trade.entity.DealCategory;
import com.jiseong.homesense.trade.entity.HousingType;
import com.jiseong.homesense.trade.entity.Trade;

/**
 * HOME-01 구성요소 5번(최근 조회 이력) 응답 항목. MVP 범위는 조회 대상이 항상 complex_id 단일
 * 참조라 딥링크 대상 분기 없이 complexId를 그대로 노출한다(v1.0의 housingType별 분기는
 * CLAUDE.md 8장 참조).
 *
 * <p>sido/sigungu/dongRi는 {@code ComplexSummaryResponse}가 이미 노출하는 것과 정확히 같은 세 필드다
 * — 백엔드 어디에도 이 세 값을 하나의 "address" 문자열로 이어붙이는 기존 로직이 없어(프론트엔드
 * {@code ComplexCard.tsx}가 {@code sigungu + " " + dongRi} 형태로 직접 조합), 여기서 새로
 * 이어붙이는 규칙을 만드는 대신 ComplexSummaryResponse와 동일한 원시 필드 3개를 그대로 노출해 프론트가
 * 같은 조합 로직을 재사용할 수 있게 했다(CPX-RCV-RGN 카드 표시 필드 보강 작업). 셋 다 nullable이다
 * (단지 기본정보 xlsx 원본 미기재 가능) — 값이 없으면 NULL을 그대로 노출한다.
 *
 * <p>price/area/floor는 해당 단지의 대표 거래(취소되지 않은 거래 중 최신 1건)에서 가져온다 —
 * {@code TradeRepository.findRecentTradesByComplexIds()}(SVC-FAV-01이 먼저 도입, SVC-CPX-01.getPopular()도
 * 재사용)를 그대로 재사용해 단일 소스를 유지한다. price 계산(SALE→dealAmount, RENT→depositAmount)도
 * {@code ComplexSummaryResponse.of()}/{@code FavoritePropertySummaryResponse.of()}와 동일한 분기를
 * 그대로 반복한다 — 새 헬퍼로 추출하지 않는 이유는 이 프로젝트가 이미 두 곳에서 이 3줄 삼항식을
 * 인라인으로 중복해온 기존 관례를 그대로 따른 것이다. 대표 거래가 없으면(recentView가 가리키는
 * 단지에 거래가 없는 이론상의 경우) 셋 다 null이다 — 예외를 던지지 않는다.
 */
public record RecentViewResponse(
        Long complexId,
        String complexName,
        HousingType housingType,
        String sido,
        String sigungu,
        String dongRi,
        Long price,
        BigDecimal area,
        Short floor,
        LocalDateTime viewedAt) {

    public static RecentViewResponse from(RecentView recentView, Trade representativeTrade) {
        Long price = representativeTrade == null ? null
                : representativeTrade.getDealCategory() == DealCategory.SALE
                        ? representativeTrade.getDealAmount()
                        : representativeTrade.getDepositAmount();

        return new RecentViewResponse(
                recentView.getComplex().getComplexId(),
                recentView.getComplex().getComplexName(),
                recentView.getHousingType(),
                recentView.getComplex().getSido(),
                recentView.getComplex().getSigungu(),
                recentView.getComplex().getDongRi(),
                price,
                representativeTrade == null ? null : representativeTrade.getExcluUseArea(),
                representativeTrade == null ? null : representativeTrade.getFloor(),
                recentView.getViewedAt());
    }
}
