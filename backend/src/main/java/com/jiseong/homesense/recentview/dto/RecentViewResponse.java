package com.jiseong.homesense.recentview.dto;

import java.time.LocalDateTime;

import com.jiseong.homesense.recentview.entity.RecentView;
import com.jiseong.homesense.trade.entity.HousingType;

/**
 * HOME-01 구성요소 5번(최근 조회 이력) 응답 항목. MVP 범위는 조회 대상이 항상 complex_id 단일
 * 참조라 딥링크 대상 분기 없이 complexId를 그대로 노출한다(v1.0의 housingType별 분기는
 * CLAUDE.md 8장 참조).
 */
public record RecentViewResponse(Long complexId, String complexName, HousingType housingType, LocalDateTime viewedAt) {

    public static RecentViewResponse from(RecentView recentView) {
        return new RecentViewResponse(
                recentView.getComplex().getComplexId(),
                recentView.getComplex().getComplexName(),
                recentView.getHousingType(),
                recentView.getViewedAt());
    }
}
