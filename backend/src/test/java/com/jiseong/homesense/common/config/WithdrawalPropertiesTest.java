package com.jiseong.homesense.common.config;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.boot.validation.autoconfigure.ValidationAutoConfiguration;
import org.springframework.context.annotation.Configuration;

/**
 * COM-CFG-01 방식 — 탈퇴 유예기간 설정이 기본값 7일로 바인딩되고, 잘못된 값(0 이하, 숫자가 아닌 값)은 기동 시점에
 * 즉시 실패(fail-fast)하는지 검증한다. {@code @Min}/{@code @Validated}는 Spring이 바인딩하는 시점에만 적용되므로
 * 레코드를 직접 생성하지 않고 ApplicationContextRunner로 실제 바인딩 경로를 재현한다
 * ({@link ExternalApiPropertiesTest}와 같은 이유).
 */
class WithdrawalPropertiesTest {

    private final ApplicationContextRunner contextRunner = new ApplicationContextRunner()
            .withConfiguration(AutoConfigurations.of(ValidationAutoConfiguration.class))
            .withUserConfiguration(WithdrawalTestConfig.class);

    @Test
    void 프로퍼티가_없어도_기본값으로_바인딩된다() {
        contextRunner.run(context -> {
            assertThat(context).hasNotFailed();
            WithdrawalProperties props = context.getBean(WithdrawalProperties.class);
            assertThat(props.graceDays()).isEqualTo(7);
            assertThat(props.purge().enabled()).isTrue();
            assertThat(props.purge().cron()).isEqualTo("0 0 5 * * *");
        });
    }

    @Test
    void 설정값이_있으면_그_값으로_바인딩된다() {
        contextRunner
                .withPropertyValues(
                        "homesense.withdrawal.grace-days=14",
                        "homesense.withdrawal.purge.enabled=false",
                        "homesense.withdrawal.purge.cron=0 30 4 * * *")
                .run(context -> {
                    WithdrawalProperties props = context.getBean(WithdrawalProperties.class);
                    assertThat(props.graceDays()).isEqualTo(14);
                    assertThat(props.purge().enabled()).isFalse();
                    assertThat(props.purge().cron()).isEqualTo("0 30 4 * * *");
                });
    }

    @Test
    void graceDays가_0이면_기동에_실패한다() {
        contextRunner.withPropertyValues("homesense.withdrawal.grace-days=0")
                .run(context -> assertThat(context).hasFailed());
    }

    @Test
    void graceDays가_음수면_기동에_실패한다() {
        contextRunner.withPropertyValues("homesense.withdrawal.grace-days=-3")
                .run(context -> assertThat(context).hasFailed());
    }

    @Test
    void graceDays가_숫자가_아니면_기동에_실패한다() {
        contextRunner.withPropertyValues("homesense.withdrawal.grace-days=abc")
                .run(context -> assertThat(context).hasFailed());
    }

    @Test
    void cron이_공백이면_기동에_실패한다() {
        contextRunner.withPropertyValues("homesense.withdrawal.purge.cron= ")
                .run(context -> assertThat(context).hasFailed());
    }

    @Configuration
    @EnableConfigurationProperties(WithdrawalProperties.class)
    static class WithdrawalTestConfig {
    }
}
