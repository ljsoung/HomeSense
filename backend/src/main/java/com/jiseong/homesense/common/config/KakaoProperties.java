package com.jiseong.homesense.common.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * COM-CFG-01. homesense.external.kakao.* 설정을 바인딩한다.
 * 실제 값은 KAKAO_API_KEY 환경변수(.env)에서 오며 코드에는 절대 하드코딩하지 않는다(NFR-4).
 *
 * <p>지오코딩(BAT-GEO-01)은 3단계(지도 기반 조회) 범위라 아직 구현되지 않았고, 이 키를 소비하는
 * 코드도 없다 — 그래서 다른 두 시크릿(DataGoKrProperties.serviceKey, JwtProperties.secret)과 달리
 * 지금은 {@code @NotBlank}를 걸지 않는다. 걸어 버리면 BAT-GEO-01을 구현하지 않은 1~2단계 배포에서도
 * KAKAO_API_KEY가 없다는 이유로 애플리케이션 기동 자체가 막힌다. BAT-GEO-01을 구현하는 시점에
 * {@code @Validated}와 {@code @NotBlank}를 추가하라(DataGoKrProperties와 동일한 패턴).
 */
@ConfigurationProperties(prefix = "homesense.external.kakao")
public record KakaoProperties(String apiKey) {
}
