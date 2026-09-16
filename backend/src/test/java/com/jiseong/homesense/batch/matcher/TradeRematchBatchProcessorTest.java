package com.jiseong.homesense.batch.matcher;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

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

    @InjectMocks
    private TradeRematchBatchProcessor processor;

    private static Trade trade(Long id, Complex complex, MatchMethod matchMethod) {
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
                .legalDistrictCode(LegalDistrictCode.builder()
                        .legalDongCd("1168010100")
                        .sidoName("서울특별시")
                        .sigunguName("강남구")
                        .eupmyeondongName("역삼동")
                        .build())
                .build();
    }

    private static Trade unmatchedTrade(Long id) {
        return trade(id, null, null);
    }

    @Test
    void 매칭에_성공한_행만_applyRematch를_호출하고_실패한_행은_건너뛴다() {
        Trade rematchable = unmatchedTrade(1L);
        Trade stillUnmatched = unmatchedTrade(2L);
        when(tradeRepository.findByComplexIsNullAndLegalDistrictCodeIsNotNullAndTradeIdGreaterThanOrderByTradeIdAsc(
                eq(0L), any()))
                .thenReturn(List.of(rematchable, stillUnmatched));
        when(complexMasterMatcher.matchComplex(any(), any()))
                .thenReturn(MatchResult.exact(99L, new BigDecimal("1.000")))
                .thenReturn(MatchResult.unmatched());

        BatchOutcome outcome = processor.processUnmatchedBatch(0L);

        assertThat(outcome.hasMore()).isTrue();
        assertThat(outcome.lastTradeId()).isEqualTo(2L);
        assertThat(outcome.changed()).isEqualTo(1);
        assertThat(outcome.unchanged()).isEqualTo(1);
        org.mockito.Mockito.verify(tradeRepository).applyRematch(eq(1L), eq(99L), eq(MatchMethod.EXACT.name()),
                eq(new BigDecimal("1.000")), any());
        org.mockito.Mockito.verify(tradeRepository, never()).applyRematch(eq(2L), anyLong(), any(), any(), any());
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
        Trade misassigned = trade(3L, staleWrongMatch, MatchMethod.SIMILAR);
        LocalDateTime cutoff = LocalDateTime.of(2026, 9, 16, 13, 0);

        when(tradeRepository.findByLegalDistrictCodeIsNotNullAndUpdatedAtBeforeAndTradeIdGreaterThanOrderByTradeIdAsc(
                eq(cutoff), eq(0L), any()))
                .thenReturn(List.of(misassigned));
        when(complexMasterMatcher.matchComplex(any(), any()))
                .thenReturn(MatchResult.exact(7L, new BigDecimal("1.000")));

        BatchOutcome outcome = processor.processUpdatedBeforeBatch(cutoff, 0L);

        assertThat(outcome.changed()).isEqualTo(1);
        assertThat(outcome.unchanged()).isZero();
        org.mockito.Mockito.verify(tradeRepository).applyRematch(eq(3L), eq(7L), eq(MatchMethod.EXACT.name()),
                eq(new BigDecimal("1.000")), any());
    }

    @Test
    void 이전과_결과가_같으면_applyRematch를_호출하지_않고_unchanged로만_집계한다() {
        // 이미 올바르게 EXACT로 매칭된 행이 다시 대상에 포함돼도(멱등) 불필요한 갱신이 없어야 한다.
        Complex alreadyCorrect = Complex.builder().complexId(7L).build();
        Trade correct = trade(4L, alreadyCorrect, MatchMethod.EXACT);
        LocalDateTime cutoff = LocalDateTime.of(2026, 9, 16, 13, 0);

        when(tradeRepository.findByLegalDistrictCodeIsNotNullAndUpdatedAtBeforeAndTradeIdGreaterThanOrderByTradeIdAsc(
                eq(cutoff), eq(0L), any()))
                .thenReturn(List.of(correct));
        when(complexMasterMatcher.matchComplex(any(), any()))
                .thenReturn(MatchResult.exact(7L, new BigDecimal("1.000")));

        BatchOutcome outcome = processor.processUpdatedBeforeBatch(cutoff, 0L);

        assertThat(outcome.unchanged()).isEqualTo(1);
        assertThat(outcome.changed()).isZero();
        org.mockito.Mockito.verify(tradeRepository, never()).applyRematch(any(), any(), any(), any(), any());
    }
}
