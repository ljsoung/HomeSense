package com.jiseong.homesense.trade.dto;

import java.math.BigDecimal;
import java.time.LocalDate;

import com.jiseong.homesense.trade.entity.DealCategory;
import com.jiseong.homesense.trade.entity.HousingType;
import com.jiseong.homesense.trade.entity.RentType;
import com.jiseong.homesense.trade.entity.Trade;

/**
 * SRCH-01(리스트형 보기) 검색 결과 목록 항목. ComplexSummaryResponse와 달리 단지 단위로 대표 거래
 * 하나를 뽑지 않고 거래 건 하나하나를 그대로 노출한다 — amount는 dealCategory가 SALE이면
 * dealAmount, RENT면 depositAmount(보증금)다(TradeSearchCondition 문서 참고). monthlyRentAmount는
 * WOLSE 거래에서만 값을 가지므로 amount와 별도 필드로 둔다.
 *
 * <p>sido/sigungu/dongRi는 trade.legal_dong_cd 매칭에 성공한 건에서만 채워진다 — 매칭 실패
 * 거래도 정상 노출되므로(trade 엔티티 legalDistrictCode 필드 문서 참고) 이 셋은 전부 nullable이다.
 * complexId/complexName도 마찬가지로 단지 마스터 매칭에 성공한 건에서만 채워진다.
 */
public record TradeSummaryResponse(
        Long tradeId,
        Long complexId,
        String complexName,
        String buildingName,
        HousingType housingType,
        DealCategory dealCategory,
        RentType rentType,
        String sido,
        String sigungu,
        String dongRi,
        LocalDate dealDate,
        BigDecimal excluUseArea,
        Short floor,
        Long amount,
        Long monthlyRentAmount) {

    public static TradeSummaryResponse of(Trade t) {
        Long amount = t.getDealCategory() == DealCategory.RENT ? t.getDepositAmount() : t.getDealAmount();

        return new TradeSummaryResponse(
                t.getTradeId(),
                t.getComplex() != null ? t.getComplex().getComplexId() : null,
                t.getComplex() != null ? t.getComplex().getComplexName() : null,
                t.getBuildingName(),
                t.getHousingType(),
                t.getDealCategory(),
                t.getRentType(),
                t.getLegalDistrictCode() != null ? t.getLegalDistrictCode().getSidoName() : null,
                t.getLegalDistrictCode() != null ? t.getLegalDistrictCode().getSigunguName() : null,
                t.getLegalDistrictCode() != null ? t.getLegalDistrictCode().getEupmyeondongName() : null,
                t.getDealDate(),
                t.getExcluUseArea(),
                t.getFloor(),
                amount,
                t.getMonthlyRentAmount());
    }
}
