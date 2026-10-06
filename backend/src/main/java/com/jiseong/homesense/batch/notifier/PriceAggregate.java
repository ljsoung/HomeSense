package com.jiseong.homesense.batch.notifier;

import java.math.BigDecimal;
import java.math.RoundingMode;

/**
 * 3.3㎡당 매매가(만원)의 합과 건수. 합·건수는 더할 수 있어 법정동별 집계를 관심 지역 prefix 단위로 합쳐도
 * 평균이 정확하다.
 */
record PriceAggregate(BigDecimal sum, long count) {

    static final PriceAggregate EMPTY = new PriceAggregate(BigDecimal.ZERO, 0);

    /** 3.3㎡ = 3.3058㎡. TradeRepository.findAveragePricePerPyeongForSale과 같은 상수·식(거래별 정규화 후 평균). */
    private static final BigDecimal PYEONG = new BigDecimal("3.3058");

    PriceAggregate plus(PriceAggregate other) {
        return new PriceAggregate(sum.add(other.sum), count + other.count);
    }

    PriceAggregate plus(NewTradeRow sale) {
        BigDecimal perPyeong = BigDecimal.valueOf(sale.dealAmount())
                .multiply(PYEONG)
                .divide(sale.excluUseArea(), 10, RoundingMode.HALF_UP);
        return new PriceAggregate(sum.add(perPyeong), count + 1);
    }

    BigDecimal average() {
        return PriceChangeCalculator.average(sum, count);
    }
}
