package com.jiseong.homesense.complex.dto;

import java.math.BigDecimal;
import java.time.LocalDate;

import com.jiseong.homesense.complex.entity.Complex;
import com.jiseong.homesense.trade.entity.DealCategory;
import com.jiseong.homesense.trade.entity.HousingType;
import com.jiseong.homesense.trade.entity.MatchMethod;
import com.jiseong.homesense.trade.entity.Trade;

/**
 * SRCH-01 검색 결과 목록 항목. representativeAmount는 dealCategory가 SALE이면 dealAmount, RENT면
 * depositAmount다(ComplexSearchRequest 문서 참고) — 어느 쪽인지는 representativeDealCategory로 구분한다.
 *
 * <p>matchMethod/floor는 대표 거래(representativeTrade)에서 그대로 가져온다 — search()/getPopular()
 * 둘 다 price/area/dealDate와 동일한 대표 거래 엔티티를 이미 통째로 들고 있어({@code search()}는
 * QueryDSL {@code select(complex, trade)}로 Trade 전체를, {@code getPopular()}는
 * {@code findFirstByComplex_ComplexIdAndCancelYnFalseOrderByDealDateDesc()}로 Trade 전체를 각각
 * 이미 조회해 두므로, 새 서브쿼리나 별도 조회 없이 같은 Trade 인스턴스에서 두 필드만 추가로 읽으면
 * 된다 — "서로 다른 거래에서 따로 조회"할 위험 자체가 없다. 둘 다 nullable이라(match_method는 매칭
 * 실패 시 NULL, floor는 원본 미기재 시 NULL) 값이 없으면 NULL을 그대로 응답한다.
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
        BigDecimal representativeArea,
        MatchMethod matchMethod,
        Short floor) {

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
                representativeTrade.getExcluUseArea(),
                representativeTrade.getMatchMethod(),
                representativeTrade.getFloor());
    }
}
