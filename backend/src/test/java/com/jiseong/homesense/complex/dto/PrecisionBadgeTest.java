package com.jiseong.homesense.complex.dto;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import com.jiseong.homesense.trade.entity.MatchMethod;

class PrecisionBadgeTest {

    @ParameterizedTest(name = "matchMethod={0}, locationPrecision={1} → {2}")
    @CsvSource(nullValues = "NULL", value = {
            "EXACT,   PRECISE,     PRECISE",
            "EXACT,   NULL,        PRECISE",
            "EXACT,   APPROXIMATE, APPROXIMATE",
            "SIMILAR, PRECISE,     APPROXIMATE",
            "SIMILAR, NULL,        APPROXIMATE",
            "SIMILAR, APPROXIMATE, APPROXIMATE",
            "NULL,    APPROXIMATE, APPROXIMATE",
            "NULL,    PRECISE,     NULL",
            "NULL,    NULL,        NULL",
    })
    void 유사_매칭이거나_근사_좌표면_근사다(MatchMethod matchMethod, String locationPrecision, PrecisionBadge expected) {
        assertThat(PrecisionBadge.of(matchMethod, locationPrecision)).isEqualTo(expected);
    }
}
