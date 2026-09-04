package com.jiseong.homesense.common.config;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Configuration;

/**
 * COM-CFG-01. 아직 전용 소비자가 없는 외부연동 설정(KakaoProperties)을 바인딩만 해 둔다.
 * DataGoKrProperties·JwtProperties는 각각 소비자(OpenApiRestClientConfig의 BAT-CLC-01,
 * SecurityConfig의 COM-SEC-01/02)가 이미 {@code @EnableConfigurationProperties}로 등록하므로
 * 여기서 다시 등록하지 않는다 — 이 클래스는 아직 소비자가 없는 설정의 임시 등록처다. BAT-GEO-01이
 * 구현되면 KakaoProperties를 그 소비자의 설정 클래스로 옮기고 이 클래스는 삭제하라.
 */
@Configuration
@EnableConfigurationProperties(KakaoProperties.class)
public class ExternalApiConfig {
}
