package com.jiseong.homesense.favorite.dto;

import java.math.BigDecimal;

import com.jiseong.homesense.favorite.entity.FavoriteRegion;
import com.jiseong.homesense.region.dto.RegionAutocompleteResponse;
import com.jiseong.homesense.region.dto.RegionStats;

/**
 * MY-02 관심 지역 리스트 카드 한 건. RGN 도메인의 {@code RegionStatsCalculator}를 그대로
 * 재사용한다(설계서 Service 표 "RGN 도메인 집계 로직 재사용") — HOME-01 전용
 * {@code InterestRegionSummaryResponse}와 같은 RegionStats를 소비하지만, MY-02가 추가로 요구하는
 * "3.3㎡당 평균가"·"신규거래 건수"까지 함께 노출한다는 점이 다르다(CLAUDE.md SVC-RGN-01 절 —
 * FAV-01/MY-02 착수 시 완결하기로 미리 표시해 둔 필드).
 */
public record FavoriteRegionSummaryResponse(
        Long favoriteRegionId,
        String legalDongCd,
        String fullPath,
        BigDecimal avgPrice,
        BigDecimal changeRate,
        BigDecimal pricePerPyeong,
        long newTradeCount) {

    public static FavoriteRegionSummaryResponse of(FavoriteRegion favoriteRegion, RegionStats stats) {
        RegionAutocompleteResponse region = RegionAutocompleteResponse.from(favoriteRegion.getLegalDistrictCode());
        return new FavoriteRegionSummaryResponse(
                favoriteRegion.getFavoriteRegionId(),
                region.legalDongCd(),
                region.fullPath(),
                stats.avgPrice(),
                stats.changeRate(),
                stats.pricePerPyeong(),
                stats.newTradeCount());
    }
}
