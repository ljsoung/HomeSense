package com.jiseong.homesense.region.dto;

import java.math.BigDecimal;

import com.jiseong.homesense.favorite.entity.FavoriteRegion;

/** HOME-01 관심 지역 요약 카드 한 건. */
public record InterestRegionSummaryResponse(
        Long favoriteRegionId,
        String legalDongCd,
        String fullPath,
        BigDecimal avgPrice,
        BigDecimal changeRate) {

    public static InterestRegionSummaryResponse of(FavoriteRegion favoriteRegion, RegionStats stats) {
        RegionAutocompleteResponse region = RegionAutocompleteResponse.from(favoriteRegion.getLegalDistrictCode());
        return new InterestRegionSummaryResponse(
                favoriteRegion.getFavoriteRegionId(),
                region.legalDongCd(),
                region.fullPath(),
                stats.avgPrice(),
                stats.changeRate());
    }
}
