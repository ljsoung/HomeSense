package com.jiseong.homesense.batch.parser.dto;

import java.math.BigDecimal;
import java.time.LocalDate;

import com.jiseong.homesense.trade.entity.DealCategory;
import com.jiseong.homesense.trade.entity.HousingType;
import com.jiseong.homesense.trade.entity.MatchMethod;
import com.jiseong.homesense.trade.entity.RentType;

/**
 * BAT-PRS-01(TradeFieldMapper)이 국토부 API 원본 XML(RawTradeItem)을 통합 스키마로 매핑한 결과.
 * complexId/legalDongCd/matchMethod/matchConfidence는 파싱 단계에서는 전부 null이고, BAT-MAT-01
 * (법정동코드 매핑)·BAT-MAT-02(단지 마스터 매칭)가 이후 단계에서 채운다 — BAT-LOD-01(TradeDataLoader)은
 * "이미 매칭이 끝난 draft가 들어온다"는 전제로만 동작하며, MAT-01→MAT-02를 체이닝해 이 필드들을 채워
 * 넘기는 오케스트레이션(Spring Batch Step 배선 또는 임시 코디네이터)은 별도 후속 작업이다.
 * latitude/longitude/location_precision/dedup_hash는 여전히 이 record에 포함하지 않는다 — 각각
 * BAT-GEO-01(지오코딩은 적재 이후 단계)과 BAT-LOD-01 내부 계산(dedup_hash는 저장 대상 컬럼이 아니라
 * TradeDataLoader가 그때그때 계산하는 파생값)의 책임이라 draft가 들고 다닐 이유가 없다.
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
        Short floor,
        Short buildYear,
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
        LocalDate cancelDate,
        Long complexId,
        String legalDongCd,
        MatchMethod matchMethod,
        BigDecimal matchConfidence) {
}
