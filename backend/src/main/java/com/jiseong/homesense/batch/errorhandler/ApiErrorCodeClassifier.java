package com.jiseong.homesense.batch.errorhandler;

import java.util.Set;

import org.springframework.stereotype.Component;

/**
 * BAT-ERR-01(1차 판정). 국토부 Open API 응답의 header/resultCode를 CONTINUE/RETRY/
 * ABORT_COMBINATION/ABORT_BATCH로 분류한다. RETRY·ABORT 시의 실제 재시도·중단 처리는
 * 이 분류 결과를 넘겨받는 호출부(BAT-SCH-01)의 책임이다.
 */
@Component
public class ApiErrorCodeClassifier {

    private static final Set<String> CONTINUE_CODES = Set.of("000", "03");
    private static final Set<String> RETRY_CODES = Set.of("01", "02", "04", "05", "22");
    private static final Set<String> ABORT_COMBINATION_CODES = Set.of("10", "11", "12", "20", "32");
    private static final Set<String> ABORT_BATCH_CODES = Set.of("30", "31");

    public ErrorCodeJudgment checkResultCode(String resultCode) {
        if (CONTINUE_CODES.contains(resultCode)) {
            return ErrorCodeJudgment.CONTINUE;
        }
        if (RETRY_CODES.contains(resultCode)) {
            return ErrorCodeJudgment.RETRY;
        }
        if (ABORT_COMBINATION_CODES.contains(resultCode)) {
            return ErrorCodeJudgment.ABORT_COMBINATION;
        }
        if (ABORT_BATCH_CODES.contains(resultCode)) {
            return ErrorCodeJudgment.ABORT_BATCH;
        }
        // 판정표에 없는 코드는 원인 파악 전까지 안전하게 해당 조합만 중단시킨다.
        return ErrorCodeJudgment.ABORT_COMBINATION;
    }
}
