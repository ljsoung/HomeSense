package com.jiseong.homesense.common.config;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableScheduling;

/**
 * BAT-SCH-01(TradeCollectionScheduler)·BAT-USR-01(WithdrawnUserPurgeScheduler)의 @Scheduled 진입점을 활성화한다.
 * WithdrawalProperties는 스케줄러뿐 아니라 탈퇴·철회 서비스(WithdrawalPolicy)도 쓰므로 항상 등록한다.
 */
@Configuration
@EnableScheduling
@EnableConfigurationProperties({BatchSchedulerProperties.class, RetryQueueProperties.class, WithdrawalProperties.class})
public class SchedulingConfig {
}
