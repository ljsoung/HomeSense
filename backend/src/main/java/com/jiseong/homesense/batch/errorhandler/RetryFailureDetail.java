package com.jiseong.homesense.batch.errorhandler;

/**
 * 재시도 실패 시 API가 실제로 알려준 오류 정보. OpenApiResultCodeException처럼 datasetId·
 * resultCode를 가진 오류라면 그 값을 그대로 담아, 재시도가 소진되거나(backoffSchedule 초과)
 * 대기 예산 초과·배치 조기 중단으로 중단될 때도 batch_log에 실제 API 오류를 남길 수 있게 한다.
 * 전송 계층 실패(RestClientException)처럼 특정 데이터셋에 귀속시킬 수 없는 오류는
 * {@link #generic(String)}으로 메시지만 보존하고, 아직 한 번도 시도되지 않은 경우엔
 * {@link #EMPTY}를 쓴다 — 두 경우 모두 datasetId·resultCode가 없어 호출부가 조합의 대표
 * 데이터셋과 "N/A"로 대체한다.
 */
public record RetryFailureDetail(String datasetId, String resultCode, String message) {

    public static final RetryFailureDetail EMPTY = new RetryFailureDetail(null, null, null);

    public static RetryFailureDetail generic(String message) {
        return new RetryFailureDetail(null, null, message);
    }
}
