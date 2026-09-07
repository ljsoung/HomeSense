package com.jiseong.homesense.complex.dto;

import java.util.List;

/**
 * MAP-01 GET /map 응답. 설계서 Controller 표는 {@code List<ComplexMapPointResponse>}를 반환 타입으로
 * 적었지만, 바로 아래 처리 로직은 결과가 상한을 넘으면 truncated 플래그를 포함하라고 명시한다 —
 * 순수 List로는 그 플래그를 실을 자리가 없어(COM-RES-01의 ApiResponse도 pageMeta 외 별도 슬롯이
 * 없다) 이 얇은 래퍼로 감쌌다(CLAUDE.md SVC-CPX-01 절 참고).
 */
public record ComplexMapSearchResponse(List<ComplexMapPointResponse> points, boolean truncated) {
}
