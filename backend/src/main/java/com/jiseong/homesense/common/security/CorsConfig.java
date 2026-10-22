package com.jiseong.homesense.common.security;

import java.time.Duration;
import java.util.List;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;

import com.jiseong.homesense.common.config.CorsProperties;

/**
 * COM-SEC-01. Spring Security {@code http.cors()}가 쓰는 CORS 설정. 허용 출처가 비어 있으면(로컬 — Vite 프록시) 아무 경로도
 * 등록하지 않아 교차 출처 요청은 브라우저에서 막힌다.
 *
 * <p>토큰은 쿠키가 아니라 {@code Authorization} 헤더로 보내므로 {@code allowCredentials}를 켜지 않는다. 허용 헤더는 프론트가
 * 실제로 보내는 것(인증, JSON 본문, 비로그인 조회 이력의 {@code X-Session-Id})만 둔다.
 */
@Configuration
@EnableConfigurationProperties(CorsProperties.class)
public class CorsConfig {

    static final List<String> ALLOWED_METHODS = List.of("GET", "POST", "PUT", "PATCH", "DELETE", "OPTIONS");
    static final List<String> ALLOWED_HEADERS = List.of("Authorization", "Content-Type", "X-Session-Id");

    @Bean
    public CorsConfigurationSource corsConfigurationSource(CorsProperties properties) {
        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        List<String> origins = properties.origins();
        if (origins.isEmpty()) {
            return source;
        }
        CorsConfiguration config = new CorsConfiguration();
        config.setAllowedOrigins(origins);
        config.setAllowedMethods(ALLOWED_METHODS);
        config.setAllowedHeaders(ALLOWED_HEADERS);
        config.setAllowCredentials(false);
        config.setMaxAge(Duration.ofHours(1));
        source.registerCorsConfiguration("/api/**", config);
        return source;
    }
}
