package com.jiseong.homesense.common.config;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Configuration;

/** {@link NotificationHistoryProperties}를 등록한다(SVC-NTF-01 알림 이력 조회 기간). */
@Configuration
@EnableConfigurationProperties(NotificationHistoryProperties.class)
public class NotificationHistoryConfig {
}
