package com.jiseong.homesense.batch.loader;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Collections;
import java.util.List;
import java.util.Set;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.ApplicationEventPublisher;

import com.jiseong.homesense.batch.parser.dto.TradeDraft;
import com.jiseong.homesense.trade.entity.DealCategory;
import com.jiseong.homesense.trade.entity.HousingType;

@ExtendWith(MockitoExtension.class)
class TradeDataLoaderTest {

    @Mock
    private TradeChunkLoader tradeChunkLoader;
    @Mock
    private ApplicationEventPublisher eventPublisher;
    @Captor
    private ArgumentCaptor<List<TradeDraft>> chunkCaptor;

    @InjectMocks
    private TradeDataLoader loader;

    private static TradeDraft dummyDraft() {
        return new TradeDraft(
                HousingType.APT, DealCategory.SALE, null, "15126468", "11680", "역삼동", "역삼래미안",
                "123-4", new BigDecimal("84.99"), (short) 10, (short) 2005, LocalDate.of(2024, 1, 15),
                120000L, null, null, null, null, null, null, null, null, null, false, null,
                1L, "1168010100", null, null);
    }

    @Test
    void 빈_목록이면_아무_청크도_처리하지_않고_이벤트도_발행하지_않는다() {
        LoadResult result = loader.loadBatch(List.of());

        assertThat(result).isEqualTo(new LoadResult(0, 0, 0, 0));
        verify(tradeChunkLoader, never()).loadChunk(any());
        verify(eventPublisher, never()).publishEvent(any());
    }

    @Test
    void 청크_크기_이하면_청크_하나로_처리하고_결과를_그대로_반환한다() {
        List<TradeDraft> drafts = Collections.nCopies(10, dummyDraft());
        ChunkOutcome outcome = new ChunkOutcome(
                new LoadResult(10, 0, 8, 2), Set.of(1L, 2L), Set.of("1168010100"));
        when(tradeChunkLoader.loadChunk(anyList())).thenReturn(outcome);

        LoadResult result = loader.loadBatch(drafts);

        assertThat(result).isEqualTo(new LoadResult(10, 0, 8, 2));
        verify(tradeChunkLoader, times(1)).loadChunk(anyList());
        verify(eventPublisher, times(1)).publishEvent(
                new TradeCacheEvictionEvent(Set.of(1L, 2L), Set.of("1168010100")));
    }

    @Test
    void 청크_크기를_넘으면_두_청크로_나눠_처리한다() {
        List<TradeDraft> drafts = Collections.nCopies(501, dummyDraft());
        ChunkOutcome outcome = new ChunkOutcome(new LoadResult(1, 0, 1, 0), Set.of(), Set.of());
        when(tradeChunkLoader.loadChunk(anyList())).thenReturn(outcome);

        loader.loadBatch(drafts);

        verify(tradeChunkLoader, times(2)).loadChunk(chunkCaptor.capture());
        List<List<TradeDraft>> chunks = chunkCaptor.getAllValues();
        assertThat(chunks.get(0)).hasSize(500);
        assertThat(chunks.get(1)).hasSize(1);
    }

    @Test
    void 청크_처리_중_예외가_발생해도_다음_청크는_계속_처리하고_청크_전체를_실패로_집계한다() {
        List<TradeDraft> chunk1 = Collections.nCopies(500, dummyDraft());
        List<TradeDraft> chunk2 = Collections.nCopies(1, dummyDraft());
        List<TradeDraft> drafts = new java.util.ArrayList<>();
        drafts.addAll(chunk1);
        drafts.addAll(chunk2);

        when(tradeChunkLoader.loadChunk(anyList()))
                .thenThrow(new RuntimeException("커밋 실패"))
                .thenReturn(new ChunkOutcome(new LoadResult(1, 0, 1, 0), Set.of(3L), Set.of()));

        LoadResult result = loader.loadBatch(drafts);

        assertThat(result).isEqualTo(new LoadResult(1, 500, 1, 0));
        verify(eventPublisher, times(1)).publishEvent(new TradeCacheEvictionEvent(Set.of(3L), Set.of()));
    }

    @Test
    void 영향받은_complexId_legalDongCd가_없으면_이벤트를_발행하지_않는다() {
        List<TradeDraft> drafts = Collections.nCopies(1, dummyDraft());
        when(tradeChunkLoader.loadChunk(anyList()))
                .thenReturn(new ChunkOutcome(new LoadResult(0, 1, 0, 0), Set.of(), Set.of()));

        loader.loadBatch(drafts);

        verify(eventPublisher, never()).publishEvent(any());
    }
}
