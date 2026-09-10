package com.jiseong.homesense.common.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import io.swagger.v3.oas.models.Components;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.security.SecurityRequirement;
import io.swagger.v3.oas.models.security.SecurityScheme;

/**
 * springdoc-openapi 기본 문서 정보와 JWT Bearer 인증 스킴을 등록한다.
 * 인증이 필요한 엔드포인트(SecurityConfig 참고)를 Swagger UI에서 바로 호출할 수 있도록
 * "Authorize" 버튼에 Access Token을 입력하면 모든 요청에 Authorization 헤더가 실린다.
 */
@Configuration
public class OpenApiConfig {

    private static final String BEARER_AUTH = "bearerAuth";

    @Bean
    public OpenAPI homeSenseOpenApi() {
        return new OpenAPI()
                .info(new Info()
                        .title("HomeSense API")
                        .description("국토교통부 실거래가 기반 개인화 부동산 시세 조회 서비스 API")
                        .version("v1"))
                .addSecurityItem(new SecurityRequirement().addList(BEARER_AUTH))
                .components(new Components()
                        .addSecuritySchemes(BEARER_AUTH, new SecurityScheme()
                                .name(BEARER_AUTH)
                                .type(SecurityScheme.Type.HTTP)
                                .scheme("bearer")
                                .bearerFormat("JWT")));
    }
}
