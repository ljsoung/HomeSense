package com.jiseong.homesense.complex.dto;

import java.math.BigDecimal;
import java.util.List;

import com.jiseong.homesense.trade.entity.DealCategory;
import com.jiseong.homesense.trade.entity.HousingType;

/**
 * SVC-CPX-01.search() 입력.
 *
 * <p>keyword(신규 제안, CLAUDE.md API-SEARCH-01 절 참고)는 SVC-SEARCH-01.record()에 그대로 전달돼
 * 인기검색어 집계에만 쓰인다 — 이번 범위에서는 실제 단지 검색 결과를 필터링하지 않는다(지성 확인:
 * 로깅 전용). 기존 호출부(테스트 등)를 건드리지 않도록 keyword 없는 12-인자 생성자를 별도로 남겨
 * keyword=null로 위임한다.
 */
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
        SortCondition sort,
        String keyword) {

    public ComplexSearchCondition(List<HousingType> housingTypes, DealCategory dealCategory, BigDecimal areaMin,
            BigDecimal areaMax, Long amountMin, Long amountMax, Short buildYearMin, Short buildYearMax,
            String sido, String sigungu, String dongRi, SortCondition sort) {
        this(housingTypes, dealCategory, areaMin, areaMax, amountMin, amountMax, buildYearMin, buildYearMax,
                sido, sigungu, dongRi, sort, null);
    }
}
