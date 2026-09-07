package com.jiseong.homesense.complex.dto;

import java.util.Locale;

import com.jiseong.homesense.complex.exception.InvalidSortConditionException;

/**
 * SVC-CPX-01.search() 정렬 기준 허용 목록(최신순/금액순/면적순). 세 기준 모두 검색조건을 만족하는
 * 거래 중 "가장 최근 거래" 하나를 각 단지의 대표 거래로 삼아, 그 거래의 dealDate/금액/면적으로
 * 정렬한다 — 기준마다 다른 거래를 고르지 않는다(지성 확인, CLAUDE.md SVC-CPX-01 절 참고).
 */
public enum SortCondition {
    LATEST,
    AMOUNT,
    AREA;

    /** 파라미터가 없으면 LATEST를 기본값으로 쓰고, 허용 목록 밖의 값이면 InvalidSortConditionException. */
    public static SortCondition from(String value) {
        if (value == null || value.isBlank()) {
            return LATEST;
        }
        try {
            return SortCondition.valueOf(value.toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException e) {
            throw new InvalidSortConditionException();
        }
    }
}
