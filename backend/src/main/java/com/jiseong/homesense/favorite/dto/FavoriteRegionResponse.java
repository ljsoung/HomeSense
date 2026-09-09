package com.jiseong.homesense.favorite.dto;

import java.time.LocalDateTime;

import com.jiseong.homesense.favorite.entity.FavoriteRegion;
import com.jiseong.homesense.region.dto.RegionAutocompleteResponse;

/** POST /api/favorites/regions 응답 — 등록 직후 결과 1건. */
public record FavoriteRegionResponse(
        Long favoriteRegionId,
        String legalDongCd,
        String fullPath,
        LocalDateTime registeredAt) {

    public static FavoriteRegionResponse from(FavoriteRegion favorite) {
        RegionAutocompleteResponse region = RegionAutocompleteResponse.from(favorite.getLegalDistrictCode());
        return new FavoriteRegionResponse(
                favorite.getFavoriteRegionId(), region.legalDongCd(), region.fullPath(), favorite.getRegisteredAt());
    }
}
