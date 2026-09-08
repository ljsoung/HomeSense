package com.jiseong.homesense.trade.dto;

import java.util.Locale;

import com.jiseong.homesense.trade.exception.InvalidSortConditionException;

/**
 * SVC-TRD-01.search() 정렬 기준. ComplexController의 {@code /api/complexes/search}와 같은 SRCH-01
 * 화면이 리스트형(개별 거래) 보기로 전환됐을 때 쓰는 API라, 사용자에게 노출되는 정렬 옵션(최신순/
 * 금액순/면적순)을 complex.dto.SortCondition과 동일하게 맞춘다 — 다만 trade가 complex.dto를
 * 그대로 참조하면 패키지 의존이 양방향으로 꼬여(trade/exception/InvalidSortConditionException 참고)
 * trade 도메인에 별도로 둔다.
 */
public enum TradeSortCondition {
    LATEST,
    AMOUNT,
    AREA;

    /** 파라미터가 없으면 LATEST를 기본값으로 쓰고, 허용 목록 밖의 값이면 InvalidSortConditionException. */
    public static TradeSortCondition from(String value) {
        if (value == null || value.isBlank()) {
            return LATEST;
        }
        try {
            return TradeSortCondition.valueOf(value.toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException e) {
            throw new InvalidSortConditionException();
        }
    }
}
