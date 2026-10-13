package com.jiseong.homesense.complex.dto;

import java.math.BigDecimal;
import java.util.List;

import com.jiseong.homesense.trade.entity.DealCategory;
import com.jiseong.homesense.trade.entity.HousingType;
import com.jiseong.homesense.trade.entity.RentType;

/**
 * MAP-01 지도 범위 조회 필터. 파라미터 이름과 뜻은 SRCH-01 검색({@link ComplexSearchRequest})과 같다 — UI정의서
 * 4.4절이 두 화면의 필터를 그대로 이어 쓰게 하므로, 같은 {@link ComplexSearchCondition}으로 바꿔 같은 QueryDSL
 * 조건 코드를 탄다. 금액은 매매면 거래금액, 전월세(rentType 지정 포함)면 보증금 기준이다.
 *
 * <p>지역 조건은 bounds가 대신하므로 regionCode·keyword는 받지 않는다(보내도 무시된다). 정렬도 없다 — 결과는
 * complex_id 순이다. 검색과 달리 "조건이 하나는 있어야 한다" 규칙도 없다.
 */
public record MapFilterRequest(
        List<HousingType> housingTypes,
        DealCategory dealCategory,
        RentType rentType,
        BigDecimal areaMin,
        BigDecimal areaMax,
        Long amountMin,
        Long amountMax,
        Short buildYearMin,
        Short buildYearMax) {

    public ComplexSearchCondition toCondition() {
        return ComplexSearchCondition.builder()
                .housingTypes(housingTypes)
                .dealCategory(dealCategory)
                .rentType(rentType)
                .areaMin(areaMin)
                .areaMax(areaMax)
                .amountMin(amountMin)
                .amountMax(amountMax)
                .buildYearMin(buildYearMin)
                .buildYearMax(buildYearMax)
                .build();
    }
}
