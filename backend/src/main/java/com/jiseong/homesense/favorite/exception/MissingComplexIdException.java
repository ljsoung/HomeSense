package com.jiseong.homesense.favorite.exception;

import org.springframework.http.HttpStatus;

import com.jiseong.homesense.common.exception.BusinessException;

/**
 * SVC-FAV-01.addFavoriteProperty() — complexId가 NULL인 경우. trade.exception의 동일 이름
 * 예외(SVC-TRD-01.getHistory())와 클래스를 공유하지 않는다 — "도메인별 수직 패키지" 원칙(CLAUDE.md
 * 패키지 구조 절, SVC-TRD-01이 TradeSortCondition을 complex.dto.SortCondition과 분리한 것과 같은
 * 이유)에 따라 각 도메인이 자기 예외를 소유한다.
 *
 * <p>AddFavoritePropertyRequest.complexId에 Bean Validation(@NotNull)을 걸지 않고 이 검사를 Service
 * 처리 로직 1단계에 그대로 둔 이유는 설계서가 이 순서를 명시하기 때문이다 — Bean Validation으로
 * 옮기면 COM-VAL-01의 범용 VALIDATION_FAILED로 응답이 바뀌어 이 클래스가 무의미해진다.
 */
public class MissingComplexIdException extends BusinessException {

    public MissingComplexIdException() {
        super("MISSING_COMPLEX_ID", "단지 ID는 필수입니다", HttpStatus.BAD_REQUEST);
    }
}
