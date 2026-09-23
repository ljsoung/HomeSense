package com.jiseong.homesense.common.exception;

import org.springframework.http.HttpStatus;

/**
 * 검색어가 {@link com.jiseong.homesense.common.validation.SearchKeywordPolicy}를 어겼을 때(길이 2~50자
 * 밖, 또는 기록 API에서 비어 있음). 단지 검색(API-CPX-01)과 검색 기록(API-SEARCH-01)이 같은 규칙을
 * 공유하므로 두 도메인이 함께 쓰는 공통 예외로 둔다.
 */
public class InvalidSearchKeywordException extends BusinessException {

    public InvalidSearchKeywordException() {
        super("INVALID_SEARCH_KEYWORD", "검색어는 2자 이상 50자 이하로 입력해주세요", HttpStatus.BAD_REQUEST);
    }
}
