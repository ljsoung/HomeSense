package com.jiseong.homesense.batch.loader;

import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;
import java.time.LocalDate;

import org.junit.jupiter.api.Test;

import com.jiseong.homesense.batch.parser.dto.TradeDraft;
import com.jiseong.homesense.trade.entity.DealCategory;
import com.jiseong.homesense.trade.entity.HousingType;
import com.jiseong.homesense.trade.entity.RentType;

class DedupHashCalculatorTest {

    private final DedupHashCalculator calculator = new DedupHashCalculator();

    private static TradeDraft saleDraft(Long complexId, Short floor, long dealAmount) {
        return new TradeDraft(
                HousingType.APT, DealCategory.SALE, null, "15126468", "11680", "역삼동", "역삼래미안",
                "123-4", new BigDecimal("84.99"), floor, (short) 2005, LocalDate.of(2024, 1, 15),
                dealAmount, null, null, "101동", "AGENT", "강남구", null, null, null, null, false, null,
                complexId, "1168010100", null, null);
    }

    private static TradeDraft rentDraft(Long complexId, Long deposit, Long monthlyRent) {
        return new TradeDraft(
                HousingType.APT, DealCategory.RENT, RentType.WOLSE, "15126474", "11680", "역삼동", "역삼래미안",
                "123-4", new BigDecimal("84.99"), (short) 10, (short) 2005, LocalDate.of(2024, 1, 15),
                null, deposit, monthlyRent, "101동", "AGENT", "강남구", null, null, null, null, false, null,
                complexId, "1168010100", null, null);
    }

    @Test
    void 동일한_입력이면_항상_같은_해시를_반환한다() {
        String hash1 = calculator.calculate(saleDraft(1L, (short) 10, 120000L));
        String hash2 = calculator.calculate(saleDraft(1L, (short) 10, 120000L));

        assertThat(hash1).isEqualTo(hash2);
    }

    @Test
    void SHA256_16진수_64자로_반환한다() {
        String hash = calculator.calculate(saleDraft(1L, (short) 10, 120000L));

        assertThat(hash).hasSize(64);
        assertThat(hash).matches("^[0-9a-f]{64}$");
    }

    @Test
    void 매매금액이_다르면_다른_해시를_반환한다() {
        String hash1 = calculator.calculate(saleDraft(1L, (short) 10, 120000L));
        String hash2 = calculator.calculate(saleDraft(1L, (short) 10, 130000L));

        assertThat(hash1).isNotEqualTo(hash2);
    }

    @Test
    void 전월세는_월세가_다르면_다른_해시를_반환한다() {
        String rent1 = calculator.calculate(rentDraft(1L, 50000000L, 300000L));
        String rent2 = calculator.calculate(rentDraft(1L, 50000000L, 400000L));

        assertThat(rent1).isNotEqualTo(rent2);
    }

    @Test
    void 전월세는_보증금이_다르면_다른_해시를_반환한다() {
        // 월세가 같아도 보증금만 달라져도 해시가 달라져야 한다 — monthlyRentAmount만 반영하고
        // depositAmount를 빠뜨리는 구현 실수를 잡기 위한 회귀 테스트.
        String rent1 = calculator.calculate(rentDraft(1L, 50000000L, 300000L));
        String rent2 = calculator.calculate(rentDraft(1L, 60000000L, 300000L));

        assertThat(rent1).isNotEqualTo(rent2);
    }

    @Test
    void 같은_숫자라도_매매와_전월세는_금액_구간_형식이_달라_다른_해시를_반환한다() {
        // SALE은 dealAmount 그대로, RENT는 "depositAmount+monthlyRentAmount" 형태라 형식 자체가 다르다
        // — 두 분기가 실제로 분리 구현됐는지 확인한다(하나로 뭉뚱그려 우연히 같은 문자열이 되면 안 된다).
        String sale = calculator.calculate(saleDraft(1L, (short) 10, 120000L));
        String rent = calculator.calculate(rentDraft(1L, 120000L, 0L));

        assertThat(sale).isNotEqualTo(rent);
    }

    @Test
    void complexId가_null이어도_예외없이_해시를_계산한다() {
        String matched = calculator.calculate(saleDraft(1L, (short) 10, 120000L));
        String unmatched = calculator.calculate(saleDraft(null, (short) 10, 120000L));

        assertThat(unmatched).hasSize(64);
        assertThat(unmatched).isNotEqualTo(matched);
    }

    @Test
    void floor가_null이어도_예외없이_해시를_계산한다() {
        String hash = calculator.calculate(saleDraft(1L, null, 120000L));

        assertThat(hash).hasSize(64);
    }
}
