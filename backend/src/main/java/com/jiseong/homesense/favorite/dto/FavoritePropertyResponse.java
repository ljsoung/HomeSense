package com.jiseong.homesense.favorite.dto;

import java.time.LocalDateTime;

import com.jiseong.homesense.favorite.entity.FavoriteProperty;
import com.jiseong.homesense.trade.entity.HousingType;

/** POST /api/favorites/properties 응답 — 등록 직후 결과 1건. */
public record FavoritePropertyResponse(
        Long favoritePropertyId,
        Long complexId,
        String complexName,
        HousingType housingType,
        LocalDateTime registeredAt) {

    public static FavoritePropertyResponse from(FavoriteProperty favorite) {
        return new FavoritePropertyResponse(
                favorite.getFavoritePropertyId(),
                favorite.getComplex().getComplexId(),
                favorite.getComplex().getComplexName(),
                favorite.getHousingType(),
                favorite.getRegisteredAt());
    }
}
