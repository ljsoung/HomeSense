package com.jiseong.homesense.batch.collector;

import com.jiseong.homesense.batch.errorhandler.ErrorCodeJudgment;

/**
 * BAT-ERR-01의 판정이 CONTINUE가 아닐 때 던져 이 조합의 수집을 즉시 중단한다.
 * 실제 RETRY(지수 백오프)·ABORT_COMBINATION·ABORT_BATCH 처리는 judgment를 보고
 * 호출부(BAT-SCH-01)가 수행한다 — BAT-CLC-01은 판정과 중단까지만 책임진다.
 */
public class OpenApiResultCodeException extends RuntimeException {

    private final String datasetId;
    private final String resultCode;
    private final ErrorCodeJudgment judgment;

    public OpenApiResultCodeException(String datasetId, String resultCode, ErrorCodeJudgment judgment) {
        super("dataset=" + datasetId + " resultCode=" + resultCode + " judgment=" + judgment);
        this.datasetId = datasetId;
        this.resultCode = resultCode;
        this.judgment = judgment;
    }

    public String datasetId() {
        return datasetId;
    }

    public String resultCode() {
        return resultCode;
    }

    public ErrorCodeJudgment judgment() {
        return judgment;
    }
}
