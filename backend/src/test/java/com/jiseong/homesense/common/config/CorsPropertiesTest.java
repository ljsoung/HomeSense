package com.jiseong.homesense.common.config;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.boot.validation.autoconfigure.ValidationAutoConfiguration;
import org.springframework.context.annotation.Configuration;

/** COM-CFG-01. 운영(required=true)에서 허용 출처가 비면 기동 실패, 로컬(기본)은 비어도 기동. */
class CorsPropertiesTest {

    private final ApplicationContextRunner contextRunner = new ApplicationContextRunner()
            .withConfiguration(AutoConfigurations.of(ValidationAutoConfiguration.class))
            .withUserConfiguration(TestConfig.class);

    @Test
    void 운영에서_허용_출처가_비어_있으면_기동에_실패한다() {
        contextRunner.withPropertyValues("homesense.cors.required=true", "homesense.cors.allowed-origins=")
                .run(context -> assertThat(context).hasFailed());
        contextRunner.withPropertyValues("homesense.cors.required=true", "homesense.cors.allowed-origins= , ")
                .run(context -> assertThat(context).hasFailed());
        contextRunner.withPropertyValues("homesense.cors.required=true")
                .run(context -> assertThat(context).hasFailed());
    }

    @Test
    void 와일드카드나_끝_슬래시는_거부한다() {
        contextRunner.withPropertyValues("homesense.cors.allowed-origins=*")
                .run(context -> assertThat(context).hasFailed());
        contextRunner.withPropertyValues("homesense.cors.allowed-origins=https://hmss.site/")
                .run(context -> assertThat(context).hasFailed());
    }

    @Test
    void 쉼표로_구분한_출처를_공백을_걷어_받는다() {
        contextRunner.withPropertyValues("homesense.cors.required=true",
                        "homesense.cors.allowed-origins=https://hmss.site, https://www.hmss.site")
                .run(context -> {
                    assertThat(context).hasNotFailed();
                    assertThat(context.getBean(CorsProperties.class).origins())
                            .containsExactly("https://hmss.site", "https://www.hmss.site");
                });
    }

    @Test
    void 로컬_기본값은_비어_있어도_기동한다() {
        contextRunner.run(context -> {
            assertThat(context).hasNotFailed();
            assertThat(context.getBean(CorsProperties.class).origins()).isEmpty();
        });
    }

    @Configuration
    @EnableConfigurationProperties(CorsProperties.class)
    static class TestConfig {
    }
}
