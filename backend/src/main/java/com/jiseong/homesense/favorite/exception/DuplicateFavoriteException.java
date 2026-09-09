package com.jiseong.homesense.favorite.exception;

import org.springframework.http.HttpStatus;

import com.jiseong.homesense.common.exception.BusinessException;

/** SVC-FAV-01.addFavoriteProperty() — 이미 등록된 (user_id, complex_id) 조합인 경우. */
public class DuplicateFavoriteException extends BusinessException {

    public DuplicateFavoriteException() {
        super("DUPLICATE_FAVORITE", "이미 등록된 관심 매물입니다", HttpStatus.CONFLICT);
    }
}
