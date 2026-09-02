package com.jiseong.homesense.common.config;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableScheduling;

/**
 * BAT-SCH-01(TradeCollectionScheduler)의 @Scheduled 진입점을 활성화한다.
 */
@Configuration
@EnableScheduling
@EnableConfigurationProperties({BatchSchedulerProperties.class, RetryQueueProperties.class})
public class SchedulingConfig {
}
