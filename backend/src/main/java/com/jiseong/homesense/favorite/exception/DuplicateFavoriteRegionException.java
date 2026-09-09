package com.jiseong.homesense.favorite.exception;

import org.springframework.http.HttpStatus;

import com.jiseong.homesense.common.exception.BusinessException;

/** SVC-FAV-01.addFavoriteRegion() — 이미 등록된 (user_id, legal_dong_cd) 조합인 경우. */
public class DuplicateFavoriteRegionException extends BusinessException {

    public DuplicateFavoriteRegionException() {
        super("DUPLICATE_FAVORITE_REGION", "이미 등록된 지역입니다", HttpStatus.CONFLICT);
    }
}
