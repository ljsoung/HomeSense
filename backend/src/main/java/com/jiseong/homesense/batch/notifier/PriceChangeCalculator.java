package com.jiseong.homesense.batch.notifier;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.Optional;

/**
 * 가격변동 알림 판정(BAT-NTF-01 D4). 순수 함수만 있다.
 *
 * <p>변동률은 소수 첫째 자리로 반올림(HALF_UP)한 값으로 판정한다 — 제목에 보이는 수치와 임계치 판정이
 * 항상 일치하고, 임계치 0%는 "반올림 후 0.0이 아닌 변동이 있을 때마다"(MY-03 문구)가 된다.
 */
final class PriceChangeCalculator {

    private PriceChangeCalculator() {
    }

    /**
     * @param baselineAverage 기준 평균(3.3㎡당, 만원)
     * @param newAverage      신규 평균(3.3㎡당, 만원)
     * @return 반올림한 변동률(%). 기준 평균이 0 이하면 empty
     */
    static Optional<BigDecimal> changeRate(BigDecimal baselineAverage, BigDecimal newAverage) {
        if (baselineAverage == null || newAverage == null || baselineAverage.signum() <= 0) {
            return Optional.empty();
        }
        BigDecimal rate = newAverage.subtract(baselineAverage)
                .multiply(BigDecimal.valueOf(100))
                .divide(baselineAverage, 10, RoundingMode.HALF_UP);
        return Optional.of(rate.setScale(1, RoundingMode.HALF_UP));
    }

    /** 반올림 변동률이 0.0이 아니고 절댓값이 임계치 이상이면 알린다. */
    static boolean exceedsThreshold(BigDecimal roundedRate, BigDecimal thresholdPct) {
        return roundedRate.signum() != 0 && roundedRate.abs().compareTo(thresholdPct) >= 0;
    }

    static BigDecimal average(BigDecimal sum, long count) {
        if (count <= 0) {
            return null;
        }
        return sum.divide(BigDecimal.valueOf(count), 10, RoundingMode.HALF_UP);
    }
}
