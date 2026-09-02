package com.jiseong.homesense.common.exception;

import org.springframework.http.HttpStatus;

/**
 * COM-EXC-01. 도메인 예외의 공통 상위 클래스.
 * 3~4장에서 정의하는 모든 도메인 예외(예: ComplexNotFoundException)는 이 클래스를 상속해,
 * GlobalExceptionHandler가 도메인별 @ExceptionHandler 없이 단일 지점에서 처리할 수 있게 한다.
 */
public class BusinessException extends RuntimeException {

    private final String errorCode;
    private final HttpStatus httpStatus;

    public BusinessException(String errorCode, String message, HttpStatus httpStatus) {
        super(message);
        this.errorCode = errorCode;
        this.httpStatus = httpStatus;
    }

    public String errorCode() {
        return errorCode;
    }

    public HttpStatus httpStatus() {
        return httpStatus;
    }
}
