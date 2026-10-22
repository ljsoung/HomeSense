package com.jiseong.homesense.common.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

import jakarta.validation.constraints.NotBlank;

/**
 * COM-CFG-01. homesense.frontend.* 설정을 바인딩한다 — AUTH-03(비밀번호 재설정) 이메일의 재설정 링크가
 * 가리킬 프론트엔드 오리진이다(오리진 하나). CORS 허용 출처({@link CorsProperties}, 여러 개 — apex와 www)와는
 * 뜻이 달라 따로 둔다.
 */
@ConfigurationProperties(prefix = "homesense.frontend")
@Validated
public record FrontendProperties(@NotBlank String baseUrl) {
}
