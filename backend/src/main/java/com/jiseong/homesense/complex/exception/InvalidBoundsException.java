package com.jiseong.homesense.complex.exception;

import org.springframework.http.HttpStatus;

import com.jiseong.homesense.common.exception.BusinessException;

/** SVC-CPX-01.searchInBounds() — MAP-01의 bounds 파라미터(남서/북동 좌표)가 형식에 맞지 않는 경우. */
public class InvalidBoundsException extends BusinessException {

    public InvalidBoundsException() {
        super("INVALID_BOUNDS", "지도 범위 형식이 올바르지 않습니다", HttpStatus.BAD_REQUEST);
    }
}
