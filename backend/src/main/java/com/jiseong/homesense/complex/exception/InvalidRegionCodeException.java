package com.jiseong.homesense.complex.exception;

import org.springframework.http.HttpStatus;

import com.jiseong.homesense.common.exception.BusinessException;

/** regionCode가 10자리 숫자가 아닐 때(API-CPX-01 search). 존재하지 않거나 폐지된 코드는 이 예외가 아니라 빈 결과다. */
public class InvalidRegionCodeException extends BusinessException {

    public InvalidRegionCodeException() {
        super("INVALID_REGION_CODE", "지역 코드가 올바르지 않습니다", HttpStatus.BAD_REQUEST);
    }
}
