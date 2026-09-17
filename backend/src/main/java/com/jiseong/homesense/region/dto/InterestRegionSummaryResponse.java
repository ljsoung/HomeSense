package com.jiseong.homesense.region.dto;

import java.math.BigDecimal;

import com.jiseong.homesense.favorite.entity.FavoriteRegion;

/**
 * HOME-01 관심 지역 요약 카드 한 건.
 *
 * <p>tradeCount는 {@link RegionStats#newTradeCount()}를 그대로 노출한다 — RegionStatsCalculator.calculate()가
 * avgPrice/changeRate와 정확히 같은 호출 안에서 같은 기간 창(달력월이 아니라 "최근 1개월" 롤링 윈도우,
 * KST)·같은 모집단(매매 SALE만, 미취소)으로 이미 계산해 두고 있어(CLAUDE.md SVC-RGN-01 절 참고) 새
 * 쿼리나 별도 기간 정의가 필요 없다 — 한 카드 안에서 평균가와 거래건수가 서로 다른 기간을 가리키는
 * 모순도 애초에 생기지 않는다(같은 계산 호출의 부산물이므로).
 */
public record InterestRegionSummaryResponse(
        Long favoriteRegionId,
        String legalDongCd,
        String fullPath,
        BigDecimal avgPrice,
        BigDecimal changeRate,
        long tradeCount) {

    public static InterestRegionSummaryResponse of(FavoriteRegion favoriteRegion, RegionStats stats) {
        RegionAutocompleteResponse region = RegionAutocompleteResponse.from(favoriteRegion.getLegalDistrictCode());
        return new InterestRegionSummaryResponse(
                favoriteRegion.getFavoriteRegionId(),
                region.legalDongCd(),
                region.fullPath(),
                stats.avgPrice(),
                stats.changeRate(),
                stats.newTradeCount());
    }
}
