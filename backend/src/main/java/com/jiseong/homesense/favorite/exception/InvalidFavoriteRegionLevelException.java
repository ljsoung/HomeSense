package com.jiseong.homesense.favorite.exception;

import org.springframework.http.HttpStatus;

import com.jiseong.homesense.common.exception.BusinessException;

/**
 * 관심 지역은 읍·면·동 단위만 등록한다(FR-5.2). 법정동코드 10자리(시도 2 · 시군구 3 · 읍면동 3 · 리 2) 중
 * 읍면동 자리가 000(시도·시군구 대표행)이거나 리 자리가 00이 아닌(리 단위) 코드, 또는 10자리 숫자가 아닌
 * 값을 거부한다. 존재하지 않거나 폐지된 읍면동 코드는 이 예외가 아니라 RegionNotFoundException(404)이다.
 */
public class InvalidFavoriteRegionLevelException extends BusinessException {

    public InvalidFavoriteRegionLevelException() {
        super("INVALID_REGION_LEVEL", "읍·면·동 단위 지역만 관심 지역으로 등록할 수 있습니다", HttpStatus.BAD_REQUEST);
    }
}
