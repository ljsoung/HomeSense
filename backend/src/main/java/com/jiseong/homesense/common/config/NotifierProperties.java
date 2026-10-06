package com.jiseong.homesense.common.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;
import org.springframework.validation.annotation.Validated;

import jakarta.validation.constraints.Min;

/**
 * BAT-NTF-01(WatchConditionEvaluator) 설정. homesense.notifier.* 를 바인딩한다.
 *
 * <ul>
 *   <li>{@code baselineMonths}: 가격변동 판정의 기준 평균에 쓰는 기간(런 날짜 기준 직전 N개월의 계약일).</li>
 *   <li>{@code minBaselineSamples}: 기준 평균의 최소 거래 수. 이보다 적으면 표본 부족으로 평가하지 않는다(오탐 방지).</li>
 *   <li>{@code chunkSize}: 알림 설정을 몇 건씩 한 트랜잭션으로 커밋할지.</li>
 * </ul>
 * 값은 기본값으로 고정해 쓰고, 프로퍼티는 조정 여지를 남기려고만 둔다(CLAUDE.md "BAT-NTF-01 구현 결정 사항" D4).
 */
@ConfigurationProperties(prefix = "homesense.notifier")
@Validated
public record NotifierProperties(
        @DefaultValue("3") @Min(1) int baselineMonths,
        @DefaultValue("3") @Min(1) int minBaselineSamples,
        @DefaultValue("200") @Min(1) int chunkSize) {
}
