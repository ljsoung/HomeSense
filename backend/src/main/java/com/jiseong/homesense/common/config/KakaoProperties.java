package com.jiseong.homesense.common.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

import jakarta.validation.constraints.NotBlank;

/**
 * COM-CFG-01. homesense.external.kakao.* 설정을 바인딩한다.
 * 실제 값은 KAKAO_API_KEY 환경변수(.env)에서 오며 코드에는 절대 하드코딩하지 않는다(NFR-4).
 *
 * <p>이 키는 카카오 <b>REST API 키</b>다(BAT-GEO-01 주소 검색, {@code Authorization: KakaoAK {키}}). 프론트 지도
 * SDK가 쓰는 JavaScript 키와 다르다. BAT-GEO-01이 소비자가 되면서 DataGoKrProperties와 같이 {@code @NotBlank}로
 * 기동 시점에 비어 있는지 확인한다(등록 위치: GeocodingConfig).
 */
@ConfigurationProperties(prefix = "homesense.external.kakao")
@Validated
public record KakaoProperties(@NotBlank String apiKey) {
}
