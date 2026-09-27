package com.jiseong.homesense.complex.exception;

import org.springframework.http.HttpStatus;

import com.jiseong.homesense.common.exception.BusinessException;

/**
 * 단지 검색(API-CPX-01)에 regionCode와 keyword가 둘 다 없을 때. SRCH-01은 검색 실행으로만 진입하고 전국
 * 목록 탐색은 명세에 없다 — 조건 없는 전국 조회는 1.6~2.2s라 NFR-1(200ms)도 넘는다(CLAUDE.md "단지 검색
 * 지역코드·키워드·거래유형" 절).
 */
public class MissingSearchConditionException extends BusinessException {

    public MissingSearchConditionException() {
        super("MISSING_SEARCH_CONDITION", "지역 또는 검색어를 입력해주세요", HttpStatus.BAD_REQUEST);
    }
}
