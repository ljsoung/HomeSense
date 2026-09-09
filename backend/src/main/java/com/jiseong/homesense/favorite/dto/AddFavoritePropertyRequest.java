package com.jiseong.homesense.favorite.dto;

/**
 * MY-02/DTL-01(하트 아이콘) 관심 매물 등록 요청. complexId에 {@code @NotNull}을 걸지 않는다 — Service
 * 처리 로직 1단계가 이 값을 명시적으로 검사해 {@code MissingComplexIdException}(400)을 던지도록
 * 설계서가 지정하고 있어, Bean Validation으로 옮기면 COM-VAL-01의 범용 VALIDATION_FAILED 응답으로
 * 대체되어 그 지정을 어기게 된다.
 */
public record AddFavoritePropertyRequest(Long complexId) {

    public AddFavoritePropertyCommand toCommand() {
        return new AddFavoritePropertyCommand(complexId);
    }
}
