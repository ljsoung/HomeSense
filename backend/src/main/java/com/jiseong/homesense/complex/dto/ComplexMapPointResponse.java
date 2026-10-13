package com.jiseong.homesense.complex.dto;

import java.math.BigDecimal;
import java.time.LocalDate;

import com.jiseong.homesense.complex.entity.Complex;
import com.jiseong.homesense.trade.entity.DealCategory;
import com.jiseong.homesense.trade.entity.HousingType;
import com.jiseong.homesense.trade.entity.MatchMethod;
import com.jiseong.homesense.trade.entity.RentType;
import com.jiseong.homesense.trade.entity.Trade;

/**
 * MAP-01 마커 하나. {@link ComplexSummaryResponse}(SRCH-01 카드)와 같은 이름·같은 값의 필드에 좌표 세 개를 더했다 —
 * 프론트가 지도 팝업과 목록 패널에 SRCH-01과 같은 카드 컴포넌트를 쓸 수 있게 하고, 같은 단지·같은 필터에서 마커의
 * 정밀/근사(matchMethod)와 카드 배지가 어긋나지 않게 한다. 값은 {@link ComplexSummaryResponse#of}를 거쳐 만든다
 * (금액 규칙을 두 벌로 두지 않는다).
 *
 * <p>대표 거래는 SRCH-01과 같다: 필터를 만족하는 취소되지 않은 거래 중 최신 계약일, 같으면 큰 trade_id. 필터가 없으면
 * DTL-01 상세 배지의 대표 거래(TradeRepository.findRecentTradesByComplexIds)와도 같다.
 *
 * <p>locationPrecision은 complex 컬럼 그대로다 — 주소 검색으로 찾은 좌표는 PRECISE, 키워드 장소 검색으로 찾은 좌표는
 * APPROXIMATE(BAT-GEO-01). 클러스터링은 카카오맵 JS SDK가 프론트에서 하므로 원시 좌표만 준다.
 */
public record ComplexMapPointResponse(
        Long complexId,
        String complexName,
        String sido,
        String sigungu,
        String dongRi,
        Integer householdCount,
        Short buildingCount,
        LocalDate approvalDate,
        HousingType representativeHousingType,
        DealCategory representativeDealCategory,
        LocalDate representativeDealDate,
        Long representativeAmount,
        BigDecimal representativeArea,
        MatchMethod matchMethod,
        Short floor,
        RentType rentType,
        Long monthlyRentAmount,
        BigDecimal latitude,
        BigDecimal longitude,
        String locationPrecision) {

    public static ComplexMapPointResponse of(Complex complex, Trade representativeTrade) {
        ComplexSummaryResponse s = ComplexSummaryResponse.of(complex, representativeTrade);
        return new ComplexMapPointResponse(
                s.complexId(), s.complexName(), s.sido(), s.sigungu(), s.dongRi(),
                s.householdCount(), s.buildingCount(), s.approvalDate(),
                s.representativeHousingType(), s.representativeDealCategory(), s.representativeDealDate(),
                s.representativeAmount(), s.representativeArea(), s.matchMethod(), s.floor(),
                s.rentType(), s.monthlyRentAmount(),
                complex.getLatitude(), complex.getLongitude(), complex.getLocationPrecision());
    }
}
