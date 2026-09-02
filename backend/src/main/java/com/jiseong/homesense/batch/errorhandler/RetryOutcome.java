package com.jiseong.homesense.batch.errorhandler;

/**
 * RetryQueueManager가 재시도 콜백의 결과로 받는 타입. resolved=true면 큐에서 제거되고
 * (성공했거나 더 이상 RETRY 대상이 아님), false면 failureDetail이 큐 엔트리에 보존된 채
 * 다음 백오프 단계로 재적재된다 — 이 detail은 재시도가 소진되거나 예산 초과·배치 조기 중단으로
 * 중단될 때 마지막으로 관측된 실제 API 오류를 batch_log에 남기는 데 쓰인다.
 */
public record RetryOutcome(boolean resolved, RetryFailureDetail failureDetail) {

    public static RetryOutcome success() {
        return new RetryOutcome(true, null);
    }

    public static RetryOutcome stillFailing(RetryFailureDetail failureDetail) {
        return new RetryOutcome(false, failureDetail);
    }
}
