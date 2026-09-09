package com.jiseong.homesense.favorite.exception;

import org.springframework.http.HttpStatus;

import com.jiseong.homesense.common.exception.BusinessException;

/**
 * SVC-FAV-01.addFavoriteProperty() — {@code Complex.inferHousingType()}이 null을 반환하는 경우
 * (complex_type 원본 미기재, 엔티티정의서 4.2절 기준 약 0.48%·105건). favorite_property.housing_type은
 * NOT NULL이라 이 값을 추정해 채울 수는 없다 — SVC-RCV-01.record()가 같은 조건에서 "기록을 조용히
 * 스킵"하는 것과 달리(CLAUDE.md SVC-RCV-01 절 참고), 관심 매물 등록은 비동기 fire-and-forget이 아니라
 * 사용자가 결과를 즉시 확인하는 동기 쓰기라 조용히 건너뛸 수 없다 — 잘못된 주택유형을 확정 저장하는
 * 대신 명시적으로 실패시킨다(설계서가 다루지 않은 간극, 지성 확인 필요).
 */
public class HousingTypeUndeterminedException extends BusinessException {

    public HousingTypeUndeterminedException() {
        super("HOUSING_TYPE_UNDETERMINED", "이 단지의 주택유형을 확인할 수 없어 관심 매물로 등록할 수 없습니다",
                HttpStatus.UNPROCESSABLE_CONTENT);
    }
}
