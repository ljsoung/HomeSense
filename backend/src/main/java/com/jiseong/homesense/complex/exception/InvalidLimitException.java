package com.jiseong.homesense.complex.exception;

import org.springframework.http.HttpStatus;

import com.jiseong.homesense.common.exception.BusinessException;

/**
 * SVC-CPX-01.getPopular() — limit이 허용 범위(ComplexController의 MIN/MAX_POPULAR_LIMIT)를 벗어난
 * 경우. 0 이하는 {@code PageRequest.of(0, limit)}가 IllegalArgumentException으로 500을 내고, 큰 값은
 * getPopular()가 값마다 최대 2건씩 추가 쿼리를 내는 N+1 경로라 인증 없는 엔드포인트에서 그대로
 * 받으면 자원 고갈로 이어진다(코드리뷰에서 지적됨).
 */
public class InvalidLimitException extends BusinessException {

    public InvalidLimitException() {
        super("INVALID_LIMIT", "limit 값이 올바르지 않습니다", HttpStatus.BAD_REQUEST);
    }
}
