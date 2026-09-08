package com.jiseong.homesense.favorite.exception;

import org.springframework.http.HttpStatus;

import com.jiseong.homesense.common.exception.BusinessException;

/** SVC-FAV-01.removeFavoriteProperty()/removeFavoriteRegion() — 존재하지 않는 ID로 삭제를 시도한 경우. */
public class FavoriteNotFoundException extends BusinessException {

    public FavoriteNotFoundException() {
        super("FAVORITE_NOT_FOUND", "존재하지 않는 관심 등록입니다", HttpStatus.NOT_FOUND);
    }
}
