package com.jiseong.homesense.batch.scheduler;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

import java.time.Clock;
import java.time.ZoneId;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;

import com.jiseong.homesense.common.config.WithdrawalProperties;
import com.jiseong.homesense.user.service.WithdrawalPolicy;
import com.jiseong.homesense.user.service.WithdrawnUserPurgeService;

/**
 * {@code homesense.withdrawal.purge.enabled=false}이면 스케줄러 빈 자체가 등록되지 않는다 — {@code @Scheduled}는
 * 빈이 있어야만 돌기 때문에, 빈이 없다는 사실이 곧 "로컬에서 조용히 계정이 삭제되지 않는다"의 근거다.
 */
class WithdrawnUserPurgeSchedulerConditionalTest {

    private final ApplicationContextRunner contextRunner = new ApplicationContextRunner()
            .withUserConfiguration(SchedulerDependenciesConfig.class);

    @Test
    void enabled가_false이면_스케줄러_빈이_등록되지_않는다() {
        contextRunner.withPropertyValues("homesense.withdrawal.purge.enabled=false")
                .run(context -> assertThat(context).doesNotHaveBean(WithdrawnUserPurgeScheduler.class));
    }

    @Test
    void enabled가_true이면_스케줄러_빈이_등록된다() {
        contextRunner.withPropertyValues("homesense.withdrawal.purge.enabled=true")
                .run(context -> assertThat(context).hasSingleBean(WithdrawnUserPurgeScheduler.class));
    }

    @Test
    void enabled_프로퍼티가_없으면_기본값인_활성으로_등록된다() {
        contextRunner.run(context -> assertThat(context).hasSingleBean(WithdrawnUserPurgeScheduler.class));
    }

    @Configuration
    @Import(WithdrawnUserPurgeScheduler.class)
    static class SchedulerDependenciesConfig {

        @Bean
        WithdrawnUserPurgeService purgeService() {
            return mock(WithdrawnUserPurgeService.class);
        }

        @Bean
        Clock clock() {
            return Clock.system(ZoneId.of("Asia/Seoul"));
        }

        @Bean
        WithdrawalPolicy withdrawalPolicy(Clock clock) {
            return new WithdrawalPolicy(clock,
                    new WithdrawalProperties(7, new WithdrawalProperties.Purge(true, "0 0 5 * * *")));
        }
    }
}
