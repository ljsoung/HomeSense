package com.jiseong.homesense.search.exception;

import org.springframework.http.HttpStatus;

import com.jiseong.homesense.common.exception.BusinessException;

/**
 * SVC-SEARCH-01.getPopularKeywords() — limit이 허용 범위(SearchController의 MIN/MAX_LIMIT)를 벗어난
 * 경우. ComplexController.InvalidLimitException과 같은 이유(PageRequest.of(0, limit)의 0 이하 방어 +
 * 인증 없는 엔드포인트의 자원 고갈 방지)로 도메인마다 별도로 둔다(도메인별 수직 패키지 원칙).
 */
public class InvalidLimitException extends BusinessException {

    public InvalidLimitException() {
        super("INVALID_LIMIT", "limit 값이 올바르지 않습니다", HttpStatus.BAD_REQUEST);
    }
}
