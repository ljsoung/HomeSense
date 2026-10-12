package com.jiseong.homesense.common.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;
import org.springframework.validation.annotation.Validated;

import jakarta.validation.constraints.Min;

/**
 * BAT-GEO-01(ComplexGeocodingService) 설정. homesense.geocoding.* 를 바인딩한다.
 *
 * <ul>
 *   <li>{@code requestIntervalMillis}: 카카오 호출 사이 최소 간격(초당 호출 수 제한). 기본 100ms = 초당 10건.</li>
 *   <li>{@code dailyLimit}: 하루(KST) 호출 상한. 무료 쿼터는 앱 단위로 집계되므로(NFR-11) 러너와 정기 실행이 Redis
 *       카운터 하나를 같이 센다. 닿으면 그날 실행을 멈추고 남은 단지는 다음 실행으로 넘긴다. 카카오 콘솔
 *       [통계]>[쿼터]의 일일 한도보다 낮게 잡는다.</li>
 *   <li>{@code maxRetries}/{@code retryBackoffMillis}: 429·5xx·네트워크 오류 재시도 횟수와 간격(시도마다 배수로 늘림).</li>
 *   <li>{@code maxConsecutiveTransientFailures}: 재시도까지 실패한 단지가 이만큼 연달아 나오면 그 실행을 멈춘다 —
 *       카카오 장애 중에 쿼터만 소진하지 않게 한다.</li>
 *   <li>{@code notFoundRetryDays}: "주소를 못 찾음" 단지를 다시 호출하지 않는 기간(Redis 키 TTL).</li>
 *   <li>{@code chunkSize}: 좌표 몇 건마다 커밋할지.</li>
 *   <li>{@code scheduledEnabled}: BAT-SCH-01 03:00 런 끝에 좌표 없는 단지를 처리할지.</li>
 * </ul>
 */
@ConfigurationProperties(prefix = "homesense.geocoding")
@Validated
public record GeocodingProperties(
        @DefaultValue("100") @Min(0) long requestIntervalMillis,
        @DefaultValue("10000") @Min(1) int dailyLimit,
        @DefaultValue("2") @Min(0) int maxRetries,
        @DefaultValue("1000") @Min(0) long retryBackoffMillis,
        @DefaultValue("5") @Min(1) int maxConsecutiveTransientFailures,
        @DefaultValue("30") @Min(1) int notFoundRetryDays,
        @DefaultValue("100") @Min(1) int chunkSize,
        @DefaultValue("true") boolean scheduledEnabled) {
}
