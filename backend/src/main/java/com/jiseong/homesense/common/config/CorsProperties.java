package com.jiseong.homesense.common.config;

import java.util.List;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;
import org.springframework.validation.annotation.Validated;

import jakarta.validation.constraints.AssertTrue;

/**
 * COM-CFG-01. homesense.cors.* — 운영에서 프론트(hmss.site)가 다른 오리진의 API를 부를 때 허용할 출처 목록이다.
 *
 * <p>{@code allowedOrigins}는 쉼표로 구분한 오리진(스킴+호스트[+포트], 끝 슬래시 없음)이고 {@code *}는 허용하지 않는다. 로컬은
 * Vite 프록시로 같은 오리진처럼 붙어 CORS가 필요 없으므로 비워 두고, 운영 프로필은 {@code required=true}로 비어 있으면 기동을
 * 막는다(다른 시크릿의 {@code @NotBlank}와 같은 fail-fast).
 */
@ConfigurationProperties(prefix = "homesense.cors")
@Validated
public record CorsProperties(@DefaultValue List<String> allowedOrigins, @DefaultValue("false") boolean required) {

    /** 앞뒤 공백을 걷고 빈 항목을 뺀 목록. */
    public List<String> origins() {
        return allowedOrigins.stream().map(String::trim).filter(o -> !o.isEmpty()).toList();
    }

    @AssertTrue(message = "homesense.cors.allowed-origins(CORS_ALLOWED_ORIGINS)가 비어 있습니다")
    public boolean isPresentWhenRequired() {
        return !required || !origins().isEmpty();
    }

    @AssertTrue(message = "homesense.cors.allowed-origins에는 *나 끝 슬래시 없이 오리진을 적어야 합니다(예: https://hmss.site)")
    public boolean isExplicitOrigins() {
        return origins().stream().noneMatch(o -> o.contains("*") || o.endsWith("/"));
    }
}
