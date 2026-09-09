package com.jiseong.homesense.region.dto;

import java.math.BigDecimal;

/**
 * RegionStatsCalculator.calculate() 결과. 해당 기간에 취소되지 않은 매매(SALE) 거래가 없으면
 * avgPrice/changeRate/pricePerPyeong 모두 null이고 newTradeCount는 0이다 — HOME-01은 이를
 * "데이터 없음"으로 표시한다.
 *
 * <p>pricePerPyeong/newTradeCount는 SVC-FAV-01(MY-02) 착수 시 추가됐다 — CLAUDE.md SVC-RGN-01 절이
 * "FAV-01/MY-02 착수 시 하드 체크" 대상으로 미리 표시해 둔 필드다. HOME-01이 쓰는
 * {@link InterestRegionSummaryResponse}는 이 두 필드를 그대로 무시하므로(avgPrice/changeRate만
 * 소비) 기존 계약을 깨지 않는 필드 추가다.
 */
public record RegionStats(BigDecimal avgPrice, BigDecimal changeRate, BigDecimal pricePerPyeong, long newTradeCount) {

    public static final RegionStats EMPTY = new RegionStats(null, null, null, 0);
}
