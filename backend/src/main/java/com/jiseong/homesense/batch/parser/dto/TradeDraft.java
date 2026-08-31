package com.jiseong.homesense.batch.parser.dto;

import java.math.BigDecimal;
import java.time.LocalDate;

import com.jiseong.homesense.trade.entity.DealCategory;
import com.jiseong.homesense.trade.entity.HousingType;
import com.jiseong.homesense.trade.entity.RentType;

/**
 * BAT-PRS-01(TradeFieldMapper)이 국토부 API 원본 XML(RawTradeItem)을 통합 스키마로 매핑한 결과.
 * match_method/match_confidence/complex_id/legal_dong_cd/latitude/longitude/location_precision/dedup_hash는
 * 이 단계에서 채우지 않는 컬럼이라 포함하지 않는다 — 각각 BAT-MAT-02, BAT-GEO-01, BAT-LOD-01이 채운다.
 */
public record TradeDraft(
        HousingType housingType,
        DealCategory dealCategory,
        RentType rentType,
        String datasetId,
        String sggCd,
        String umdNm,
        String buildingName,
        String jibun,
        BigDecimal excluUseArea,
        LocalDate dealDate,
        Long dealAmount,
        Long depositAmount,
        Long monthlyRentAmount,
        String aptDong,
        String dealingType,
        String agentSggNm,
        LocalDate registrationDate,
        String sellerType,
        String buyerType,
        Boolean landLeaseYn,
        boolean cancelYn,
        LocalDate cancelDate) {
}
