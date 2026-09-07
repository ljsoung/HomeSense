package com.jiseong.homesense.complex.exception;

import org.springframework.http.HttpStatus;

import com.jiseong.homesense.common.exception.BusinessException;

/** SVC-CPX-01.getDetail() — 존재하지 않는 단지 ID로 조회한 경우. */
public class ComplexNotFoundException extends BusinessException {

    public ComplexNotFoundException() {
        super("COMPLEX_NOT_FOUND", "존재하지 않는 단지입니다", HttpStatus.NOT_FOUND);
    }
}
