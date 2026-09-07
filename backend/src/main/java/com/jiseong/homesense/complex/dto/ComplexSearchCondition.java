package com.jiseong.homesense.complex.dto;

import java.math.BigDecimal;
import java.util.List;

import com.jiseong.homesense.trade.entity.DealCategory;
import com.jiseong.homesense.trade.entity.HousingType;

/** SVC-CPX-01.search() 입력. */
public record ComplexSearchCondition(
        List<HousingType> housingTypes,
        DealCategory dealCategory,
        BigDecimal areaMin,
        BigDecimal areaMax,
        Long amountMin,
        Long amountMax,
        Short buildYearMin,
        Short buildYearMax,
        String sido,
        String sigungu,
        String dongRi,
        SortCondition sort) {
}
