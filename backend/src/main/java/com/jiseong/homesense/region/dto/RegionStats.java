package com.jiseong.homesense.region.dto;

import java.math.BigDecimal;

/**
 * RegionStatsCalculator.calculate() 결과. 해당 기간에 취소되지 않은 매매(SALE) 거래가 없으면
 * avgPrice/changeRate 모두 null이다 — HOME-01은 이를 "데이터 없음"으로 표시한다.
 */
public record RegionStats(BigDecimal avgPrice, BigDecimal changeRate) {

    public static final RegionStats EMPTY = new RegionStats(null, null);
}
