package com.jiseong.homesense.complex.dto;

import java.math.BigDecimal;
import java.util.List;

import com.jiseong.homesense.trade.entity.DealCategory;
import com.jiseong.homesense.trade.entity.HousingType;

/**
 * SRCH-01 검색 요청. 주택유형(다중), 거래유형, 전용면적/거래금액/건축년도 범위, 지역(시도/시군구/동리)
 * 조건 — 전부 선택적이며 생략하면 해당 조건은 걸지 않는다.
 *
 * <p>amountMin/amountMax는 dealCategory에 따라 대상 컬럼이 갈린다 — SALE(또는 미지정)이면
 * trade.dealAmount, RENT면 trade.depositAmount(보증금)를 기준으로 삼는다. 월세(monthlyRentAmount)는
 * 별도 필터로 다루지 않는다(매매 금액과 전월세 보증금·월세를 하나의 "금액"으로 합산할 기준이
 * 스펙에 없어 범위를 넘지 않는 선에서 보증금만 대표값으로 삼았다).
 *
 * <p>buildYearMin/buildYearMax는 다른 범위 필터(전용면적·거래금액)와 달리 trade가 아니라
 * complex.approval_date(사용승인일)를 기준으로 삼는다(UI정의서 4.4절 원문) — 단지 1건당 값이
 * 고정인 approval_date와 달리 trade.build_year는 거래 건별 API 원본값이라 데이터 품질에 따라
 * 실제 사용승인일과 어긋날 수 있다.
 *
 * <p>keyword(신규 제안, CLAUDE.md API-SEARCH-01 절 참고)는 HOME-01 히어로 검색바/GNB 재검색이 보내는
 * 원문 검색어다 — 이번 범위에서는 검색 결과 필터링에 관여하지 않고 SVC-SEARCH-01.record()의
 * 인기검색어 집계 로깅에만 쓰인다(지성 확인: 로깅 전용).
 */
public record ComplexSearchRequest(
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
        String sort,
        String keyword) {

    public ComplexSearchCondition toCondition() {
        return new ComplexSearchCondition(housingTypes, dealCategory, areaMin, areaMax, amountMin, amountMax,
                buildYearMin, buildYearMax, sido, sigungu, dongRi, SortCondition.from(sort), keyword);
    }
}
