package com.jiseong.homesense.common.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;
import org.springframework.validation.annotation.Validated;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;

/**
 * API-CPX-01 GET /api/complexes/map 설정. homesense.complex.map.* 를 바인딩한다.
 *
 * <p>{@code maxResults}: 한 번에 돌려줄 마커 상한(기본 500, 프로그램설계서 3.3절). 넘으면 상한까지만 주고 truncated=true.
 * 위쪽을 5000으로 막은 이유는 응답 크기와 대표거래 서브쿼리 비용이 건수에 비례하기 때문이다.
 */
@ConfigurationProperties(prefix = "homesense.complex.map")
@Validated
public record ComplexMapProperties(@DefaultValue("500") @Min(1) @Max(5000) int maxResults) {
}
