package com.jiseong.homesense.trade.exception;

import org.springframework.http.HttpStatus;

import com.jiseong.homesense.common.exception.BusinessException;

/**
 * SVC-TRD-01.getHistory() — complexId가 NULL인 경우. v1.0에서는 complexId/officetelKey/dong
 * 3방향 다형적 필수값 검증기를 뒀으나, MVP 범위 축소로 대상 파라미터가 complexId 하나만 남아
 * 단순 필수값 검증으로 축소됐다(CLAUDE.md "최우선 규칙 — MVP 범위" 참고).
 */
public class MissingComplexIdException extends BusinessException {

    public MissingComplexIdException() {
        super("MISSING_COMPLEX_ID", "단지 ID는 필수입니다", HttpStatus.BAD_REQUEST);
    }
}
