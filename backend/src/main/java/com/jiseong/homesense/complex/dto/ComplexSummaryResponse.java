package com.jiseong.homesense.complex.dto;

import java.math.BigDecimal;
import java.time.LocalDate;

import com.jiseong.homesense.complex.entity.Complex;
import com.jiseong.homesense.trade.entity.DealCategory;
import com.jiseong.homesense.trade.entity.HousingType;
import com.jiseong.homesense.trade.entity.Trade;

/**
 * SRCH-01 검색 결과 목록 항목. representativeAmount는 dealCategory가 SALE이면 dealAmount, RENT면
 * depositAmount다(ComplexSearchRequest 문서 참고) — 어느 쪽인지는 representativeDealCategory로 구분한다.
 */
public record ComplexSummaryResponse(
        Long complexId,
        String complexName,
        String sido,
        String sigungu,
        String dongRi,
        Integer householdCount,
        Short buildingCount,
        LocalDate approvalDate,
        HousingType representativeHousingType,
        DealCategory representativeDealCategory,
        LocalDate representativeDealDate,
        Long representativeAmount,
        BigDecimal representativeArea) {

    public static ComplexSummaryResponse of(Complex complex, Trade representativeTrade) {
        Long amount = representativeTrade.getDealCategory() == DealCategory.SALE
                ? representativeTrade.getDealAmount()
                : representativeTrade.getDepositAmount();

        return new ComplexSummaryResponse(
                complex.getComplexId(),
                complex.getComplexName(),
                complex.getSido(),
                complex.getSigungu(),
                complex.getDongRi(),
                complex.getHouseholdCount(),
                complex.getBuildingCount(),
                complex.getApprovalDate(),
                representativeTrade.getHousingType(),
                representativeTrade.getDealCategory(),
                representativeTrade.getDealDate(),
                amount,
                representativeTrade.getExcluUseArea());
    }
}
