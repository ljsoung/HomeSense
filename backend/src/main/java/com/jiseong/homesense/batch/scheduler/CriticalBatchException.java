package com.jiseong.homesense.batch.scheduler;

/**
 * 서비스키 오류/만료(resultCode 30/31)가 연속으로 임계치만큼 발생했을 때 던진다.
 * BatchExecutionOrchestrator.orchestrate()가 이 예외를 받으면 남은 전체 조합 순회를 조기 중단한다.
 */
public class CriticalBatchException extends RuntimeException {

    private final String resultCode;
    private final int consecutiveFailureCount;

    public CriticalBatchException(String resultCode, int consecutiveFailureCount) {
        super("서비스키 오류(resultCode=" + resultCode + ")가 " + consecutiveFailureCount
                + "회 연속 발생해 배치 전체를 조기 중단한다");
        this.resultCode = resultCode;
        this.consecutiveFailureCount = consecutiveFailureCount;
    }

    public String resultCode() {
        return resultCode;
    }

    public int consecutiveFailureCount() {
        return consecutiveFailureCount;
    }
}
