package com.jiseong.homesense.complex.dto;

import java.util.List;

import com.jiseong.homesense.trade.entity.HousingType;

/**
 * MAP-01 지도 범위 조회 필터. 설계서에 필드 구성이 명시돼 있지 않아, 지도 마커에 실제로 의미 있는
 * 최소 조건인 주택유형(다중)만 우선 지원한다 — search()의 면적/금액/건축년도 범위까지 지도 조회에
 * 그대로 옮기면 매 팬/줌마다 나가는 가벼운 조회가 무거워진다. 필요해지면 여기에 조건을 추가하라.
 */
public record MapFilterRequest(List<HousingType> housingTypes) {

    public MapFilterCondition toCondition() {
        return new MapFilterCondition(housingTypes);
    }
}
