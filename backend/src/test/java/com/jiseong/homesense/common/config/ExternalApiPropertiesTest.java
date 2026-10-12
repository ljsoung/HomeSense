package com.jiseong.homesense.common.config;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.boot.validation.autoconfigure.ValidationAutoConfiguration;
import org.springframework.context.annotation.Configuration;

/**
 * COM-CFG-01. 값이 비어 있으면 애플리케이션 기동 시점에 즉시 실패(fail-fast)한다는 요구사항(NFR-4)을
 * 검증한다. {@code new DataGoKrProperties("")}처럼 레코드를 직접 생성하는 것만으로는 이 검증이
 * 트리거되지 않는다 — {@code @Validated}/{@code @NotBlank}는 Spring이 {@code @ConfigurationProperties}를
 * 바인딩하는 시점에만 적용되므로, 여기서는 ApplicationContextRunner로 실제 스프링 컨텍스트를 최소
 * 구성으로 띄워 그 바인딩·검증 경로를 그대로 재현한다.
 */
class ExternalApiPropertiesTest {

    private final ApplicationContextRunner contextRunner = new ApplicationContextRunner()
            .withConfiguration(AutoConfigurations.of(ValidationAutoConfiguration.class));

    @Test
    void dataGoKr_serviceKey가_비어있으면_기동에_실패한다() {
        contextRunner.withUserConfiguration(DataGoKrTestConfig.class)
                .withPropertyValues("homesense.external.data-go-kr.service-key=")
                .run(context -> assertThat(context).hasFailed());
    }

    @Test
    void dataGoKr_serviceKey가_없으면_기동에_실패한다() {
        contextRunner.withUserConfiguration(DataGoKrTestConfig.class)
                .run(context -> assertThat(context).hasFailed());
    }

    @Test
    void dataGoKr_serviceKey가_채워져_있으면_정상_기동한다() {
        contextRunner.withUserConfiguration(DataGoKrTestConfig.class)
                .withPropertyValues("homesense.external.data-go-kr.service-key=real-service-key")
                .run(context -> {
                    assertThat(context).hasNotFailed();
                    assertThat(context.getBean(DataGoKrProperties.class).serviceKey()).isEqualTo("real-service-key");
                });
    }

    @Test
    void jwt_secret이_비어있으면_기동에_실패한다() {
        contextRunner.withUserConfiguration(JwtTestConfig.class)
                .withPropertyValues(
                        "homesense.security.jwt.secret=",
                        "homesense.security.jwt.access-token-validity=1800000",
                        "homesense.security.jwt.refresh-token-validity=1209600000")
                .run(context -> assertThat(context).hasFailed());
    }

    @Test
    void jwt_secret이_채워져_있으면_정상_기동한다() {
        contextRunner.withUserConfiguration(JwtTestConfig.class)
                .withPropertyValues(
                        "homesense.security.jwt.secret=real-secret",
                        "homesense.security.jwt.access-token-validity=1800000",
                        "homesense.security.jwt.refresh-token-validity=1209600000")
                .run(context -> {
                    assertThat(context).hasNotFailed();
                    assertThat(context.getBean(JwtProperties.class).secret()).isEqualTo("real-secret");
                });
    }

    /** BAT-GEO-01이 소비자가 되면서 다른 두 시크릿과 같이 비어 있으면 기동을 막는다(KakaoProperties). */
    @Test
    void kakao_apiKey가_없으면_기동에_실패한다() {
        contextRunner.withUserConfiguration(KakaoTestConfig.class)
                .run(context -> assertThat(context).hasFailed());
    }

    @Test
    void kakao_apiKey가_비어있으면_기동에_실패한다() {
        contextRunner.withUserConfiguration(KakaoTestConfig.class)
                .withPropertyValues("homesense.external.kakao.api-key= ")
                .run(context -> assertThat(context).hasFailed());
    }

    @Test
    void kakao_apiKey가_채워져_있으면_정상_기동한다() {
        contextRunner.withUserConfiguration(KakaoTestConfig.class)
                .withPropertyValues("homesense.external.kakao.api-key=real-rest-api-key")
                .run(context -> {
                    assertThat(context).hasNotFailed();
                    assertThat(context.getBean(KakaoProperties.class).apiKey()).isEqualTo("real-rest-api-key");
                });
    }

    @Configuration
    @EnableConfigurationProperties(DataGoKrProperties.class)
    static class DataGoKrTestConfig {
    }

    @Configuration
    @EnableConfigurationProperties(JwtProperties.class)
    static class JwtTestConfig {
    }

    @Configuration
    @EnableConfigurationProperties(KakaoProperties.class)
    static class KakaoTestConfig {
    }
}
