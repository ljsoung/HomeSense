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
        return saleDraft(complexId, floor, dealAmount, "11680", "역삼동", "역삼래미안", "123-4");
    }

    private static TradeDraft saleDraft(Long complexId, Short floor, long dealAmount,
            String sggCd, String umdNm, String buildingName, String jibun) {
        return new TradeDraft(
                HousingType.APT, DealCategory.SALE, null, "15126468", sggCd, umdNm, buildingName,
                jibun, new BigDecimal("84.99"), floor, (short) 2005, LocalDate.of(2024, 1, 15),
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

    @Test
    void 미매칭_건은_시군구_동_건물명_지번이_다르면_다른_해시를_반환한다() {
        // 회귀 테스트: complexId가 둘 다 null이면 이전 구현은 두 건이 같은 "null" 리터럴로 뭉쳐 같은
        // housing_type/deal_date/floor/exclu_use_area/금액을 우연히 공유하는 서로 다른 지역의 거래가
        // 같은 해시로 충돌했다(두 번째 건이 첫 번째 건의 UPDATE로 처리되어 유실됨). 원본 주소
        // (sggCd/umdNm/buildingName/jibun)를 대체 식별자로 넣어 구분되는지 확인한다.
        String gangnam = calculator.calculate(
                saleDraft(null, (short) 10, 120000L, "11680", "역삼동", "역삼래미안", "123-4"));
        String songpa = calculator.calculate(
                saleDraft(null, (short) 10, 120000L, "11710", "잠실동", "잠실엘스", "45-6"));

        assertThat(gangnam).isNotEqualTo(songpa);
    }

    @Test
    void 매칭된_건은_주소가_달라도_complexId가_같으면_같은_해시를_반환한다() {
        // complexId로 이미 유일하게 식별되는 매칭 성공 건은 원본 주소가 부가 정보로 들어가지 않는다
        // — 문서 원문 계산식(complex_id만 사용)을 그대로 유지한다.
        String hash1 = calculator.calculate(
                saleDraft(1L, (short) 10, 120000L, "11680", "역삼동", "역삼래미안", "123-4"));
        String hash2 = calculator.calculate(
                saleDraft(1L, (short) 10, 120000L, "11710", "잠실동", "잠실엘스", "45-6"));

        assertThat(hash1).isEqualTo(hash2);
    }
}
