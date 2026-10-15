package com.jiseong.homesense.complex.dto;

import java.util.List;

/**
 * MAP-01 GET /map 응답. 설계서 Controller 표는 {@code List<ComplexMapPointResponse>}를 반환 타입으로 적었지만, 처리
 * 로직은 결과가 상한을 넘으면 truncated를 알리라고 한다 — 순수 List로는 실을 자리가 없어 이 래퍼로 감쌌다.
 *
 * @param items     complex_id의 결정적 해시 순(잘릴 때 화면 전체에서 고르게 뽑힌다), 최대 limit건
 * @param truncated 범위 안 결과가 limit을 넘어 잘렸는지 — 프론트는 "지도를 확대하세요" 같은 안내를 띄운다
 * @param limit     이번 응답에 적용한 상한(homesense.complex.map.max-results)
 */
public record ComplexMapSearchResponse(List<ComplexMapPointResponse> items, boolean truncated, int limit) {
}
