package com.jiseong.homesense.common.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

import jakarta.validation.constraints.NotBlank;

/**
 * COM-CFG-01. homesense.frontend.* 설정을 바인딩한다 — AUTH-03(비밀번호 재설정) 이메일의 재설정 링크가
 * 가리킬 프론트엔드 오리진이다. 기존 {@code homesense.cors.allowed-origins}(FRONTEND_URL)를 재사용하지
 * 않는다 — 그 값은 아직 어떤 {@code CorsConfigurationSource}도 소비하지 않는 죽은 설정(CLAUDE.md 프론트
 * 배포 절 참고)이라, 이 값에 새 기능을 얹으면 그 미완결 상태에 불필요하게 묶인다. 독립된 프로퍼티로
 * 분리해 두면 나중에 CORS 쪽이 실제로 구현될 때 서로 영향을 주지 않는다.
 */
@ConfigurationProperties(prefix = "homesense.frontend")
@Validated
public record FrontendProperties(@NotBlank String baseUrl) {
}
