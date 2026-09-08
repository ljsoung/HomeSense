package com.jiseong.homesense.trade.dto;

import java.math.BigDecimal;
import java.util.List;

import com.jiseong.homesense.trade.entity.DealCategory;
import com.jiseong.homesense.trade.entity.HousingType;
import com.jiseong.homesense.trade.entity.RentType;

/**
 * SRCH-01(리스트형 보기) 검색 요청. 전부 선택적이며 생략하면 해당 조건은 걸지 않는다
 * (TradeSearchCondition 문서 참고).
 */
public record TradeSearchRequest(
        String legalDongCd,
        List<HousingType> housingTypes,
        DealCategory dealCategory,
        RentType rentType,
        BigDecimal areaMin,
        BigDecimal areaMax,
        Long amountMin,
        Long amountMax,
        String sort) {

    public TradeSearchCondition toCondition() {
        return new TradeSearchCondition(legalDongCd, housingTypes, dealCategory, rentType, areaMin, areaMax,
                amountMin, amountMax, TradeSortCondition.from(sort));
    }
}
