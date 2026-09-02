package com.jiseong.homesense.common.config;

import java.util.List;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

/**
 * BAT-ERR-01. homesense.batch.retry.* 설정을 바인딩한다.
 * backoffMinutes의 각 값은 n번째 재시도 전 대기 시간(분)이고, 목록의 길이가 곧 최대 재시도
 * 횟수다 — 재시도 계획표와 최대 횟수를 별도 설정으로 두면 서로 어긋날 수 있어 하나로 합쳤다.
 * maxTotalWaitMinutes는 processRetryQueue() 한 번 호출에서 실제로 대기(sleep)할 수 있는
 * 누적 시간의 상한이다 — 큐에 쌓인 요청 수만큼 백오프가 그대로 누적되므로(예: 실패 조합 10건이
 * 각 3회 재시도를 다 소진하면 10 × 36분 ≈ 6시간), 이 상한이 없으면 대량 장애 시
 * orchestrate() 호출 자체가 무한정 블로킹될 수 있다. 상한을 넘는 순간부터는 남은 큐 전체를
 * 더 이상 대기·재시도하지 않고 즉시 최종 실패로 처리해 다음 배치 사이클로 이월시킨다.
 */
@ConfigurationProperties(prefix = "homesense.batch.retry")
public record RetryQueueProperties(List<Long> backoffMinutes, @DefaultValue("180") long maxTotalWaitMinutes) {
}
