package com.jiseong.homesense.batch.loader;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.jiseong.homesense.batch.parser.dto.TradeDraft;
import com.jiseong.homesense.trade.entity.DealCategory;
import com.jiseong.homesense.trade.entity.HousingType;
import com.jiseong.homesense.trade.repository.TradeRepository;

@ExtendWith(MockitoExtension.class)
class TradeChunkLoaderTest {

    private static final String FIXED_HASH = "hash-abc";

    @Mock
    private TradeRepository tradeRepository;
    @Mock
    private DedupHashCalculator dedupHashCalculator;

    @InjectMocks
    private TradeChunkLoader chunkLoader;

    private static TradeDraft draft(Long complexId, String legalDongCd) {
        return new TradeDraft(
                HousingType.APT, DealCategory.SALE, null, "15126468", "11680", "역삼동", "역삼래미안",
                "123-4", new BigDecimal("84.99"), (short) 10, (short) 2005, LocalDate.of(2024, 1, 15),
                120000L, null, null, "101동", "AGENT", "강남구", LocalDate.of(2024, 1, 20), null, null, null,
                false, null, complexId, legalDongCd, null, null);
    }

    @Test
    void upsert가_1을_반환하면_신규_INSERT로_집계한다() {
        TradeDraft draft = draft(1L, "1168010100");
        when(dedupHashCalculator.calculate(draft)).thenReturn(FIXED_HASH);
        when(tradeRepository.upsert(
                eq("APT"), eq("SALE"), isNull(), eq("15126468"), eq("11680"), eq("1168010100"), eq("역삼동"),
                eq(1L), eq("역삼래미안"), eq("123-4"), eq(new BigDecimal("84.99")), eq((short) 10),
                eq((short) 2005), eq(LocalDate.of(2024, 1, 15)), eq(120000L), isNull(), isNull(),
                eq("101동"), eq("AGENT"), eq("강남구"), eq(LocalDate.of(2024, 1, 20)), isNull(), isNull(),
                isNull(), anyBoolean(), isNull(), isNull(), isNull(), eq(FIXED_HASH), any()))
                .thenReturn(1);

        ChunkOutcome outcome = chunkLoader.loadChunk(List.of(draft));

        assertThat(outcome.result()).isEqualTo(new LoadResult(1, 0, 1, 0));
        assertThat(outcome.touchedComplexIds()).containsExactly(1L);
        assertThat(outcome.touchedLegalDongCds()).containsExactly("1168010100");
    }

    @Test
    void upsert가_2를_반환하면_UPDATE로_집계한다() {
        // useAffectedRows=true 드라이버 설정 기준(값이 바뀐 UPDATE=2) — TradeRepository#upsert javadoc 참고.
        TradeDraft draft = draft(1L, "1168010100");
        when(dedupHashCalculator.calculate(draft)).thenReturn(FIXED_HASH);
        when(tradeRepository.upsert(any(), any(), any(), any(), any(), any(), any(), any(), any(), any(),
                any(), any(), any(), any(), any(), any(), any(), any(), any(), any(), any(), any(), any(),
                any(), anyBoolean(), any(), any(), any(), any(), any()))
                .thenReturn(2);

        ChunkOutcome outcome = chunkLoader.loadChunk(List.of(draft));

        assertThat(outcome.result()).isEqualTo(new LoadResult(1, 0, 0, 1));
    }

    @Test
    void 매칭_실패로_complexId와_legalDongCd가_null이면_touched_집합에_담지_않는다() {
        TradeDraft draft = draft(null, null);
        when(dedupHashCalculator.calculate(draft)).thenReturn(FIXED_HASH);
        when(tradeRepository.upsert(any(), any(), any(), any(), any(), any(), any(), any(), any(), any(),
                any(), any(), any(), any(), any(), any(), any(), any(), any(), any(), any(), any(), any(),
                any(), anyBoolean(), any(), any(), any(), any(), any()))
                .thenReturn(1);

        ChunkOutcome outcome = chunkLoader.loadChunk(List.of(draft));

        assertThat(outcome.result()).isEqualTo(new LoadResult(1, 0, 1, 0));
        assertThat(outcome.touchedComplexIds()).isEmpty();
        assertThat(outcome.touchedLegalDongCds()).isEmpty();
    }

    @Test
    void upsert가_런타임_예외를_던지면_해당_건만_스킵하고_error_count에_반영한다() {
        TradeDraft draft = draft(1L, "1168010100");
        when(dedupHashCalculator.calculate(draft)).thenReturn(FIXED_HASH);
        when(tradeRepository.upsert(any(), any(), any(), any(), any(), any(), any(), any(), any(), any(),
                any(), any(), any(), any(), any(), any(), any(), any(), any(), any(), any(), any(), any(),
                any(), anyBoolean(), any(), any(), any(), any(), any()))
                .thenThrow(new org.springframework.dao.DataIntegrityViolationException("unexpected"));

        ChunkOutcome outcome = chunkLoader.loadChunk(List.of(draft));

        assertThat(outcome.result()).isEqualTo(new LoadResult(0, 1, 0, 0));
        assertThat(outcome.touchedComplexIds()).isEmpty();
    }

    @Test
    void 한_건이_실패해도_나머지_건은_계속_처리한다() {
        TradeDraft failingDraft = draft(1L, "1168010100");
        TradeDraft okDraft = draft(2L, "1168010200");
        when(dedupHashCalculator.calculate(failingDraft)).thenThrow(new IllegalStateException("hash 계산 실패"));
        when(dedupHashCalculator.calculate(okDraft)).thenReturn(FIXED_HASH);
        when(tradeRepository.upsert(any(), any(), any(), any(), any(), any(), any(), any(), any(), any(),
                any(), any(), any(), any(), any(), any(), any(), any(), any(), any(), any(), any(), any(),
                any(), anyBoolean(), any(), any(), any(), any(), any()))
                .thenReturn(1);

        ChunkOutcome outcome = chunkLoader.loadChunk(List.of(failingDraft, okDraft));

        assertThat(outcome.result()).isEqualTo(new LoadResult(1, 1, 1, 0));
    }
}
