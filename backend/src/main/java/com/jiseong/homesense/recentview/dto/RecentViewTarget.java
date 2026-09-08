package com.jiseong.homesense.recentview.dto;

import com.jiseong.homesense.trade.entity.HousingType;

/**
 * SVC-RCV-01.record()가 받는 조회 대상. MVP 범위(아파트·연립다세대)는 조회 대상이 항상 complex_id
 * 단일 참조라 필드가 이 둘뿐이다 — 오피스텔/단독다가구 확장 시 target 종류가 늘어나면 CLAUDE.md
 * 8장 절차대로 재설계한다(v1.0의 housingType별 분기).
 *
 * <p>housingType은 {@link com.jiseong.homesense.complex.entity.Complex#inferHousingType()}가
 * complex_type 원본 미기재(NULL, 약 0.48%)일 때 null을 돌려줄 수 있다 — record()는 이 경우 조용히
 * 기록을 스킵한다(CLAUDE.md SVC-RCV-01 절 참고).
 */
public record RecentViewTarget(Long complexId, HousingType housingType) {
}
