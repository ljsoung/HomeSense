package com.jiseong.homesense.recentview.dto;

import java.time.LocalDateTime;

import com.jiseong.homesense.recentview.entity.RecentView;
import com.jiseong.homesense.trade.entity.HousingType;

/**
 * HOME-01 구성요소 5번(최근 조회 이력) 응답 항목. MVP 범위는 조회 대상이 항상 complex_id 단일
 * 참조라 딥링크 대상 분기 없이 complexId를 그대로 노출한다(v1.0의 housingType별 분기는
 * CLAUDE.md 8장 참조).
 *
 * <p>sido/sigungu/dongRi는 {@code ComplexSummaryResponse}가 이미 노출하는 것과 정확히 같은 세 필드다
 * — 백엔드 어디에도 이 세 값을 하나의 "address" 문자열로 이어붙이는 기존 로직이 없어(프론트엔드
 * {@code ComplexCard.tsx}가 {@code sigungu + " " + dongRi} 형태로 직접 조합), 여기서 새로
 * 이어붙이는 규칙을 만드는 대신 ComplexSummaryResponse와 동일한 원시 필드 3개를 그대로 노출해 프론트가
 * 같은 조합 로직을 재사용할 수 있게 했다(CPX-RCV-RGN 카드 표시 필드 보강 작업). 셋 다 nullable이다
 * (단지 기본정보 xlsx 원본 미기재 가능) — 값이 없으면 NULL을 그대로 노출한다.
 */
public record RecentViewResponse(
        Long complexId,
        String complexName,
        HousingType housingType,
        String sido,
        String sigungu,
        String dongRi,
        LocalDateTime viewedAt) {

    public static RecentViewResponse from(RecentView recentView) {
        return new RecentViewResponse(
                recentView.getComplex().getComplexId(),
                recentView.getComplex().getComplexName(),
                recentView.getHousingType(),
                recentView.getComplex().getSido(),
                recentView.getComplex().getSigungu(),
                recentView.getComplex().getDongRi(),
                recentView.getViewedAt());
    }
}
