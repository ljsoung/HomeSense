package com.jiseong.homesense.batch.parser.dto;

import com.jiseong.homesense.trade.entity.HousingType;

/**
 * PRS-01이 국토부 API 원본 XML을 통합 스키마로 매핑한 결과 중, BAT-MAT-02(단지 마스터 매칭)가
 * 필요로 하는 필드만 담은 매칭 입력값이다. jibun·buildingName은 정규화 전 원본 값이다.
 */
public record TradeDraft(
        HousingType housingType,
        String sido,
        String sigungu,
        String dongRi,
        String jibun,
        String buildingName) {
}
