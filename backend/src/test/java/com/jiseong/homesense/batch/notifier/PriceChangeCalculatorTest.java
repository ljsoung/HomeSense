package com.jiseong.homesense.batch.notifier;

import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;

import org.junit.jupiter.api.Test;

class PriceChangeCalculatorTest {

    private static BigDecimal rate(String baseline, String fresh) {
        return PriceChangeCalculator.changeRate(new BigDecimal(baseline), new BigDecimal(fresh)).orElseThrow();
    }

    @Test
    void 변동률은_소수_첫째_자리로_반올림한다() {
        assertThat(rate("1000", "1021")).isEqualByComparingTo("2.1");
        assertThat(rate("1000", "1049.5")).isEqualByComparingTo("5.0");  // 4.95 → 5.0
        assertThat(rate("1000", "1049.4")).isEqualByComparingTo("4.9");  // 4.94 → 4.9
    }

    @Test
    void 하락은_음수로_낸다() {
        assertThat(rate("1000", "950")).isEqualByComparingTo("-5.0");
    }

    @Test
    void 기준_평균이_0이면_계산하지_않는다() {
        assertThat(PriceChangeCalculator.changeRate(BigDecimal.ZERO, BigDecimal.TEN)).isEmpty();
    }

    @Test
    void 임계치와_정확히_같으면_알린다() {
        assertThat(PriceChangeCalculator.exceedsThreshold(new BigDecimal("5.0"), new BigDecimal("5.0"))).isTrue();
        assertThat(PriceChangeCalculator.exceedsThreshold(new BigDecimal("-5.0"), new BigDecimal("5.0"))).isTrue();
        assertThat(PriceChangeCalculator.exceedsThreshold(new BigDecimal("4.9"), new BigDecimal("5.0"))).isFalse();
    }

    @Test
    void 반올림_경계_4점95는_임계치_5에서_알린다() {
        assertThat(PriceChangeCalculator.exceedsThreshold(rate("1000", "1049.5"), new BigDecimal("5.0"))).isTrue();
    }

    @Test
    void 임계치_0이면_변동_0점0은_알리지_않고_0점1은_알린다() {
        BigDecimal zero = new BigDecimal("0.0");
        assertThat(PriceChangeCalculator.exceedsThreshold(rate("1000", "1000.4"), zero)).isFalse(); // 0.04 → 0.0
        assertThat(PriceChangeCalculator.exceedsThreshold(rate("1000", "1001"), zero)).isTrue();    // 0.1
        assertThat(PriceChangeCalculator.exceedsThreshold(rate("1000", "999"), zero)).isTrue();     // -0.1
    }
}
