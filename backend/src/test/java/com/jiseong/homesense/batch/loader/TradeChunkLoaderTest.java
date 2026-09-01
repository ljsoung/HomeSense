package com.jiseong.homesense.batch.loader;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.DataIntegrityViolationException;

import com.jiseong.homesense.batch.parser.dto.TradeDraft;
import com.jiseong.homesense.complex.entity.Complex;
import com.jiseong.homesense.complex.repository.ComplexRepository;
import com.jiseong.homesense.region.entity.LegalDistrictCode;
import com.jiseong.homesense.region.repository.LegalDistrictCodeRepository;
import com.jiseong.homesense.trade.entity.DealCategory;
import com.jiseong.homesense.trade.entity.HousingType;
import com.jiseong.homesense.trade.entity.Trade;
import com.jiseong.homesense.trade.repository.TradeRepository;

@ExtendWith(MockitoExtension.class)
class TradeChunkLoaderTest {

    private static final String FIXED_HASH = "hash-abc";

    @Mock
    private TradeRepository tradeRepository;
    @Mock
    private LegalDistrictCodeRepository legalDistrictCodeRepository;
    @Mock
    private ComplexRepository complexRepository;
    @Mock
    private DedupHashCalculator dedupHashCalculator;
    @Mock
    private TradeInsertGateway tradeInsertGateway;

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
    void 기존_행이_없으면_신규_INSERT하고_결과에_inserted를_반영한다() {
        TradeDraft draft = draft(1L, "1168010100");
        when(dedupHashCalculator.calculate(draft)).thenReturn(FIXED_HASH);
        when(tradeRepository.findByDedupHash(FIXED_HASH)).thenReturn(Optional.empty());
        when(complexRepository.getReferenceById(1L)).thenReturn(Complex.builder().complexId(1L).build());
        when(legalDistrictCodeRepository.getReferenceById("1168010100"))
                .thenReturn(LegalDistrictCode.builder().legalDongCd("1168010100").build());

        ChunkOutcome outcome = chunkLoader.loadChunk(List.of(draft));

        assertThat(outcome.result()).isEqualTo(new LoadResult(1, 0, 1, 0));
        assertThat(outcome.touchedComplexIds()).containsExactly(1L);
        assertThat(outcome.touchedLegalDongCds()).containsExactly("1168010100");
        verify(tradeInsertGateway, times(1)).insert(any(Trade.class));
    }

    @Test
    void 매칭_실패로_complexId와_legalDongCd가_null이면_참조_조회를_하지_않는다() {
        TradeDraft draft = draft(null, null);
        when(dedupHashCalculator.calculate(draft)).thenReturn(FIXED_HASH);
        when(tradeRepository.findByDedupHash(FIXED_HASH)).thenReturn(Optional.empty());

        ChunkOutcome outcome = chunkLoader.loadChunk(List.of(draft));

        assertThat(outcome.result()).isEqualTo(new LoadResult(1, 0, 1, 0));
        assertThat(outcome.touchedComplexIds()).isEmpty();
        assertThat(outcome.touchedLegalDongCds()).isEmpty();
        verify(complexRepository, never()).getReferenceById(any());
        verify(legalDistrictCodeRepository, never()).getReferenceById(any());
    }

    @Test
    void 기존_행이_있으면_사후_갱신_필드만_UPDATE하고_신규_저장은_호출하지_않는다() {
        TradeDraft draft = draft(1L, "1168010100");
        Trade existing = Trade.builder()
                .cancelYn(false)
                .aptDong("102동")
                .build();
        when(dedupHashCalculator.calculate(draft)).thenReturn(FIXED_HASH);
        when(tradeRepository.findByDedupHash(FIXED_HASH)).thenReturn(Optional.of(existing));

        ChunkOutcome outcome = chunkLoader.loadChunk(List.of(draft));

        assertThat(outcome.result()).isEqualTo(new LoadResult(1, 0, 0, 1));
        assertThat(existing.getAptDong()).isEqualTo("101동");
        assertThat(existing.getRegistrationDate()).isEqualTo(LocalDate.of(2024, 1, 20));
        verify(tradeRepository, never()).saveAndFlush(any());
    }

    @Test
    void dedup_hash_충돌시_UPDATE로_1회_재시도해_성공하면_updated로_집계한다() {
        TradeDraft draft = draft(1L, "1168010100");
        Trade winner = Trade.builder().cancelYn(false).build();
        when(dedupHashCalculator.calculate(draft)).thenReturn(FIXED_HASH);
        when(tradeRepository.findByDedupHash(FIXED_HASH))
                .thenReturn(Optional.empty(), Optional.of(winner));
        when(complexRepository.getReferenceById(1L)).thenReturn(Complex.builder().complexId(1L).build());
        when(legalDistrictCodeRepository.getReferenceById("1168010100"))
                .thenReturn(LegalDistrictCode.builder().legalDongCd("1168010100").build());
        // 격리된 INSERT 시도(tradeInsertGateway)는 실패하고, 같은 청크 트랜잭션 안의 재시도 UPDATE
        // (tradeRepository.saveAndFlush)만 성공한다 — 두 호출이 서로 다른 협력자임을 명확히 구분한다.
        doThrow(new DataIntegrityViolationException("dedup_hash unique violation"))
                .when(tradeInsertGateway).insert(any(Trade.class));
        when(tradeRepository.saveAndFlush(any(Trade.class))).thenReturn(winner);

        ChunkOutcome outcome = chunkLoader.loadChunk(List.of(draft));

        assertThat(outcome.result()).isEqualTo(new LoadResult(1, 0, 0, 1));
        assertThat(winner.getAptDong()).isEqualTo("101동");
        verify(tradeRepository, times(2)).findByDedupHash(FIXED_HASH);
        verify(tradeInsertGateway, times(1)).insert(any());
        verify(tradeRepository, times(1)).saveAndFlush(any());
    }

    @Test
    void 재시도까지_실패하면_해당_건만_스킵하고_error_count에_반영한다() {
        TradeDraft draft = draft(1L, "1168010100");
        Trade winner = Trade.builder().cancelYn(false).build();
        when(dedupHashCalculator.calculate(draft)).thenReturn(FIXED_HASH);
        when(tradeRepository.findByDedupHash(FIXED_HASH))
                .thenReturn(Optional.empty(), Optional.of(winner));
        when(complexRepository.getReferenceById(1L)).thenReturn(Complex.builder().complexId(1L).build());
        when(legalDistrictCodeRepository.getReferenceById("1168010100"))
                .thenReturn(LegalDistrictCode.builder().legalDongCd("1168010100").build());
        doThrow(new DataIntegrityViolationException("first failure"))
                .when(tradeInsertGateway).insert(any(Trade.class));
        when(tradeRepository.saveAndFlush(any(Trade.class)))
                .thenThrow(new DataIntegrityViolationException("retry also fails"));

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
        when(tradeRepository.findByDedupHash(FIXED_HASH)).thenReturn(Optional.empty());
        when(complexRepository.getReferenceById(2L)).thenReturn(Complex.builder().complexId(2L).build());
        when(legalDistrictCodeRepository.getReferenceById("1168010200"))
                .thenReturn(LegalDistrictCode.builder().legalDongCd("1168010200").build());

        ChunkOutcome outcome = chunkLoader.loadChunk(List.of(failingDraft, okDraft));

        assertThat(outcome.result()).isEqualTo(new LoadResult(1, 1, 1, 0));
    }
}
