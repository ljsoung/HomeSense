package com.jiseong.homesense.trade.exception;

import org.springframework.http.HttpStatus;

import com.jiseong.homesense.common.exception.BusinessException;

/**
 * SVC-TRD-01.search() 정렬 기준(sort)이 허용 목록(LATEST/AMOUNT/AREA) 밖인 경우. complex 도메인의
 * 동명 예외와 별개 클래스다 — trade가 complex.dto를 참조하면 complex→trade(Trade 엔티티 등)와
 * trade→complex(정렬 조건) 양방향 의존이 생겨 패키지별 수직 슬라이스 원칙(CLAUDE.md)과 어긋난다.
 */
public class InvalidSortConditionException extends BusinessException {

    public InvalidSortConditionException() {
        super("INVALID_SORT_CONDITION", "정렬 조건이 올바르지 않습니다", HttpStatus.BAD_REQUEST);
    }
}
