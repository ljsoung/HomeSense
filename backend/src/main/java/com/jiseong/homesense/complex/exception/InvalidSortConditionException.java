package com.jiseong.homesense.complex.exception;

import org.springframework.http.HttpStatus;

import com.jiseong.homesense.common.exception.BusinessException;

/** SVC-CPX-01.search() — sort 값이 허용 목록(LATEST/AMOUNT/AREA) 밖인 경우. */
public class InvalidSortConditionException extends BusinessException {

    public InvalidSortConditionException() {
        super("INVALID_SORT_CONDITION", "정렬 기준이 올바르지 않습니다", HttpStatus.BAD_REQUEST);
    }
}
