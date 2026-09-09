package com.jiseong.homesense.favorite.dto;

import jakarta.validation.constraints.NotBlank;

/**
 * MY-02 관심 지역 등록 요청. legalDongCd는 SVC-FAV-01 예외표에 전용 "필수값 누락" 예외가 지정돼
 * 있지 않아(complexId의 MissingComplexIdException과 달리) COM-VAL-01의 표준 경로(@NotBlank →
 * MethodArgumentNotValidException → VALIDATION_FAILED)를 그대로 쓴다.
 */
public record AddFavoriteRegionRequest(@NotBlank String legalDongCd) {

    public AddFavoriteRegionCommand toCommand() {
        return new AddFavoriteRegionCommand(legalDongCd);
    }
}
