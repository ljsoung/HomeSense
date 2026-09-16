package com.jiseong.homesense.batch.matcher;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.jiseong.homesense.batch.loader.DedupHashCalculator;
import com.jiseong.homesense.batch.matcher.TradeRematchBatchProcessor.BatchOutcome;
import com.jiseong.homesense.complex.entity.Complex;
import com.jiseong.homesense.region.entity.LegalDistrictCode;
import com.jiseong.homesense.trade.entity.DealCategory;
import com.jiseong.homesense.trade.entity.HousingType;
import com.jiseong.homesense.trade.entity.MatchMethod;
import com.jiseong.homesense.trade.entity.Trade;
import com.jiseong.homesense.trade.repository.TradeRepository;

@ExtendWith(MockitoExtension.class)
class TradeRematchBatchProcessorTest {

    @Mock
    private TradeRepository tradeRepository;

    @Mock
    private ComplexMasterMatcher complexMasterMatcher;

    @Mock
    private DedupHashCalculator dedupHashCalculator;

    @InjectMocks
    private TradeRematchBatchProcessor processor;

    private static Trade trade(Long id, Complex complex, MatchMethod matchMethod, String dedupHash) {
        return Trade.builder()
                .tradeId(id)
                .housingType(HousingType.APT)
                .dealCategory(DealCategory.SALE)
                .datasetId("15126469")
                .sggCd("11680")
                .umdNm("역삼동")
                .buildingName("역삼래미안")
                .jibun("123-4")
                .excluUseArea(new BigDecimal("84.99"))
                .dealDate(LocalDate.of(2024, 1, 15))
                .cancelYn(false)
                .complex(complex)
                .matchMethod(matchMethod)
                .dedupHash(dedupHash)
                .legalDistrictCode(LegalDistrictCode.builder()
                        .legalDongCd("1168010100")
                        .sidoName("서울특별시")
                        .sigunguName("강남구")
                        .eupmyeondongName("역삼동")
                        .build())
                .build();
    }

    private static Trade unmatchedTrade(Long id, String dedupHash) {
        return trade(id, null, null, dedupHash);
    }

    @Test
    void 매칭에_성공한_행만_applyRematch를_호출하고_실패한_행은_건너뛴다() {
        Trade rematchable = unmatchedTrade(1L, "old-unmatched-hash-1");
        Trade stillUnmatched = unmatchedTrade(2L, "old-unmatched-hash-2");
        when(tradeRepository.findByComplexIsNullAndLegalDistrictCodeIsNotNullAndTradeIdGreaterThanOrderByTradeIdAsc(
                eq(0L), any()))
                .thenReturn(List.of(rematchable, stillUnmatched));
        when(complexMasterMatcher.matchComplex(any(), any()))
                .thenReturn(MatchResult.exact(99L, new BigDecimal("1.000")))
                .thenReturn(MatchResult.unmatched());
        when(dedupHashCalculator.calculate(any())).thenReturn("new-matched-hash-1");

        BatchOutcome outcome = processor.processUnmatchedBatch(0L);

        assertThat(outcome.hasMore()).isTrue();
        assertThat(outcome.lastTradeId()).isEqualTo(2L);
        assertThat(outcome.changed()).isEqualTo(1);
        assertThat(outcome.unchanged()).isEqualTo(1);
        verify(tradeRepository).applyRematch(eq(1L), eq(99L), eq(MatchMethod.EXACT.name()),
                eq(new BigDecimal("1.000")), eq("new-matched-hash-1"), any());
        verify(tradeRepository, never()).applyRematch(eq(2L), anyLong(), any(), any(), any(), any());
    }

    @Test
    void 배치가_비어있으면_hasMore가_false다() {
        when(tradeRepository.findByComplexIsNullAndLegalDistrictCodeIsNotNullAndTradeIdGreaterThanOrderByTradeIdAsc(
                eq(0L), any()))
                .thenReturn(List.of());

        BatchOutcome outcome = processor.processUnmatchedBatch(0L);

        assertThat(outcome.hasMore()).isFalse();
        assertThat(outcome.lastTradeId()).isNull();
        assertThat(outcome.unchanged()).isZero();
        assertThat(outcome.changed()).isZero();
    }

    @Test
    void 두번째_패스는_이미_SIMILAR로_배정된_행도_대상으로_삼아_EXACT로_재배정한다() {
        // 버그 A/B 시절 지번 비교가 막혀 있어 "그나마 비슷한 단지"(complexId=5, SIMILAR)로 배정됐던 행 —
        // complex_id가 이미 채워져 있어 1차 패스(processUnmatchedBatch)는 이 행을 건너뛴다. 2차 패스는
        // complex_id 유무와 무관하게 이 행을 다시 매처에 태워, 지번이 정확히 일치하는 complexId=7로
        // EXACT 재배정돼야 함을 검증한다.
        Complex staleWrongMatch = Complex.builder().complexId(5L).build();
        Trade misassigned = trade(3L, staleWrongMatch, MatchMethod.SIMILAR, "hash-for-complex-5");
        LocalDateTime cutoff = LocalDateTime.of(2026, 9, 16, 13, 0);

        when(tradeRepository.findByLegalDistrictCodeIsNotNullAndUpdatedAtBeforeAndTradeIdGreaterThanOrderByTradeIdAsc(
                eq(cutoff), eq(0L), any()))
                .thenReturn(List.of(misassigned));
        when(complexMasterMatcher.matchComplex(any(), any()))
                .thenReturn(MatchResult.exact(7L, new BigDecimal("1.000")));
        when(dedupHashCalculator.calculate(any())).thenReturn("hash-for-complex-7");

        BatchOutcome outcome = processor.processUpdatedBeforeBatch(cutoff, 0L);

        assertThat(outcome.changed()).isEqualTo(1);
        assertThat(outcome.unchanged()).isZero();
        verify(tradeRepository).applyRematch(eq(3L), eq(7L), eq(MatchMethod.EXACT.name()),
                eq(new BigDecimal("1.000")), eq("hash-for-complex-7"), any());
    }

    @Test
    void 이전과_결과가_같으면_applyRematch를_호출하지_않고_unchanged로만_집계한다() {
        // 이미 올바르게 EXACT로 매칭된 행이 다시 대상에 포함돼도(멱등) 불필요한 갱신이 없어야 한다.
        // complex_id가 안 바뀌므로 dedup_hash 재계산 자체가 일어나지 않아야 한다(calculate() 미호출).
        Complex alreadyCorrect = Complex.builder().complexId(7L).build();
        Trade correct = trade(4L, alreadyCorrect, MatchMethod.EXACT, "hash-for-complex-7");
        LocalDateTime cutoff = LocalDateTime.of(2026, 9, 16, 13, 0);

        when(tradeRepository.findByLegalDistrictCodeIsNotNullAndUpdatedAtBeforeAndTradeIdGreaterThanOrderByTradeIdAsc(
                eq(cutoff), eq(0L), any()))
                .thenReturn(List.of(correct));
        when(complexMasterMatcher.matchComplex(any(), any()))
                .thenReturn(MatchResult.exact(7L, new BigDecimal("1.000")));

        BatchOutcome outcome = processor.processUpdatedBeforeBatch(cutoff, 0L);

        assertThat(outcome.unchanged()).isEqualTo(1);
        assertThat(outcome.changed()).isZero();
        verify(tradeRepository, never()).applyRematch(any(), any(), any(), any(), any(), any());
        verify(dedupHashCalculator, never()).calculate(any());
    }

    @Test
    void 복구_배치는_매칭_결과를_바꾸지_않고_dedup_hash만_재계산해_다르면_갱신한다() {
        Complex complex = Complex.builder().complexId(7L).build();
        Trade staleHashRow = trade(9L, complex, MatchMethod.EXACT, "stale-hash");
        when(tradeRepository.findByTradeIdGreaterThanOrderByTradeIdAsc(eq(0L), any()))
                .thenReturn(List.of(staleHashRow));
        when(dedupHashCalculator.calculate(any())).thenReturn("correct-hash");

        BatchOutcome outcome = processor.repairDedupHashBatch(0L);

        assertThat(outcome.changed()).isEqualTo(1);
        assertThat(outcome.unchanged()).isZero();
        verify(complexMasterMatcher, never()).matchComplex(any(), any());
        verify(tradeRepository).applyRematch(eq(9L), eq(7L), eq(MatchMethod.EXACT.name()), any(),
                eq("correct-hash"), any());
    }

    @Test
    void 복구_배치는_이미_올바른_dedup_hash는_건드리지_않는다() {
        Complex complex = Complex.builder().complexId(7L).build();
        Trade correctHashRow = trade(10L, complex, MatchMethod.EXACT, "already-correct-hash");
        when(tradeRepository.findByTradeIdGreaterThanOrderByTradeIdAsc(eq(0L), any()))
                .thenReturn(List.of(correctHashRow));
        when(dedupHashCalculator.calculate(any())).thenReturn("already-correct-hash");

        BatchOutcome outcome = processor.repairDedupHashBatch(0L);

        assertThat(outcome.unchanged()).isEqualTo(1);
        assertThat(outcome.changed()).isZero();
        verify(tradeRepository, never()).applyRematch(any(), any(), any(), any(), any(), any());
    }

    @Test
    void 재계산된_dedup_hash를_이미_다른_행이_쓰고_있으면_이_행을_삭제하고_applyRematch를_호출하지_않는다() {
        // 두 행이 재매칭 결과 사실상 같은 실거래(같은 complex_id/날짜/층/면적/금액)로 수렴하는 경우 —
        // 이미 그 정체성을 정상 수집 경로로 획득한 다른 행(target)이 있다면, 이 stale 행은 중복이므로
        // UNIQUE 제약을 위반하는 대신 삭제로 정리해야 한다.
        Trade staleDuplicate = unmatchedTrade(8L, "old-unmatched-hash-8");
        Trade target = trade(20L, Complex.builder().complexId(99L).build(), MatchMethod.EXACT, "shared-target-hash");

        when(tradeRepository.findByComplexIsNullAndLegalDistrictCodeIsNotNullAndTradeIdGreaterThanOrderByTradeIdAsc(
                eq(0L), any()))
                .thenReturn(List.of(staleDuplicate));
        when(complexMasterMatcher.matchComplex(any(), any()))
                .thenReturn(MatchResult.exact(99L, new BigDecimal("1.000")));
        when(dedupHashCalculator.calculate(any())).thenReturn("shared-target-hash");
        when(tradeRepository.findByDedupHash("shared-target-hash")).thenReturn(Optional.of(target));

        BatchOutcome outcome = processor.processUnmatchedBatch(0L);

        assertThat(outcome.changed()).isEqualTo(1);
        verify(tradeRepository).deleteById(8L);
        verify(tradeRepository, never()).applyRematch(any(), any(), any(), any(), any(), any());
    }
}
