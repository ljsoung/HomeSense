package com.jiseong.homesense.batch.errorhandler;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

class ApiErrorCodeClassifierTest {

    private final ApiErrorCodeClassifier classifier = new ApiErrorCodeClassifier();

    @ParameterizedTest
    @CsvSource({
            "000, CONTINUE",
            "03, CONTINUE",
            "01, RETRY",
            "02, RETRY",
            "04, RETRY",
            "05, RETRY",
            "22, RETRY",
            "10, ABORT_COMBINATION",
            "11, ABORT_COMBINATION",
            "12, ABORT_COMBINATION",
            "20, ABORT_COMBINATION",
            "32, ABORT_COMBINATION",
            "30, ABORT_BATCH",
            "31, ABORT_BATCH",
            "99, ABORT_COMBINATION"
    })
    void 판정표대로_resultCode를_분류한다(String resultCode, ErrorCodeJudgment expected) {
        assertThat(classifier.checkResultCode(resultCode)).isEqualTo(expected);
    }
}
