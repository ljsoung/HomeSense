package com.jiseong.homesense.trade.dto;

import java.math.BigDecimal;
import java.util.List;

import com.jiseong.homesense.trade.entity.DealCategory;
import com.jiseong.homesense.trade.entity.HousingType;
import com.jiseong.homesense.trade.entity.RentType;

/**
 * SVC-TRD-01.search() 입력. idx_trade_legal_dong_deal_date(legalDongCd 필터 + deal_date 정렬)와
 * idx_trade_housing_deal_category(housingType/dealCategory 필터)를 활용하도록 설계됐다.
 *
 * <p>legalDongCd는 RGN 도메인의 지역 자동완성(GET /api/regions?query=)에서 선택된 법정동코드 1건을
 * 그대로 받는다 — ComplexSearchCondition처럼 sido/sigungu/dongRi 세 문자열로 받지 않는 이유는,
 * trade 테이블 자체에는 그 세 컬럼이 없고(legal_dong_cd로 legal_district_code를 참조할 뿐) 문자열
 * 완전일치 3중 조건보다 이미 사용자가 자동완성으로 확정한 코드 하나로 거는 쪽이 idx_trade_legal_dong_deal_date를
 * 그대로 태울 수 있어 더 가볍다.
 *
 * <p>amountMin/amountMax의 대상 컬럼은 ComplexSearchCondition과 같은 규칙이다 — dealCategory가
 * RENT면 depositAmount(보증금), 그 외(SALE·미지정)면 dealAmount.
 */
public record TradeSearchCondition(
        String legalDongCd,
        List<HousingType> housingTypes,
        DealCategory dealCategory,
        RentType rentType,
        BigDecimal areaMin,
        BigDecimal areaMax,
        Long amountMin,
        Long amountMax,
        TradeSortCondition sort) {
}
