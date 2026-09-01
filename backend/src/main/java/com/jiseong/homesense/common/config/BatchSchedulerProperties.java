package com.jiseong.homesense.common.config;

import java.util.List;

import org.springframework.boot.context.properties.ConfigurationProperties;

import com.jiseong.homesense.trade.entity.HousingType;

/**
 * BAT-SCH-01. homesense.batch.scheduler.* 설정을 바인딩한다.
 * housingTypes는 FR-2.2에 따라 1단계는 APT만, 2단계부터는 APT,VILLA로 코드 변경 없이
 * 이 설정값만 넓히면 되도록 만든 확장 지점이다.
 */
@ConfigurationProperties(prefix = "homesense.batch.scheduler")
public record BatchSchedulerProperties(List<HousingType> housingTypes) {
}
