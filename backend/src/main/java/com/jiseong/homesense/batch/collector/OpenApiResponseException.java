package com.jiseong.homesense.batch.collector;

/**
 * 응답 구조 자체(header/resultCode, XML 형식)를 읽을 수 없을 때 던진다.
 * resultCode는 정상적으로 읽었지만 값이 CONTINUE가 아닌 경우는 {@link OpenApiResultCodeException}을 쓴다.
 */
public class OpenApiResponseException extends RuntimeException {

    public OpenApiResponseException(String message) {
        super(message);
    }

    public OpenApiResponseException(String message, Throwable cause) {
        super(message, cause);
    }
}
