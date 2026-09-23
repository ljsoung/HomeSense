package com.jiseong.homesense.common.config;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableScheduling;

/**
 * BAT-SCH-01(TradeCollectionScheduler)·BAT-USR-01(WithdrawnUserPurgeScheduler)의 @Scheduled 진입점을 활성화한다.
 * WithdrawalProperties는 스케줄러뿐 아니라 탈퇴·철회 서비스(WithdrawalPolicy)도 쓰므로 항상 등록한다.
 *
 * <p>{@code @EnableScheduling}만 {@code homesense.scheduling.enabled}(기본 true)로 끌 수 있게 분리했다 —
 * 1회성 유지보수 러너(예: {@code backfill-complex-legal-dong} 프로필)가 컨텍스트를 띄우는 동안
 * 03:00 수집 파이프라인 같은 {@code @Scheduled} 작업이 함께 돌지 않게 하기 위해서다.
 */
@Configuration
@EnableConfigurationProperties({BatchSchedulerProperties.class, RetryQueueProperties.class, WithdrawalProperties.class})
public class SchedulingConfig {

    @Configuration
    @EnableScheduling
    @ConditionalOnProperty(prefix = "homesense.scheduling", name = "enabled", havingValue = "true", matchIfMissing = true)
    static class SchedulingEnabledConfig {
    }
}
