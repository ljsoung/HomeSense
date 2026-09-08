package com.jiseong.homesense.trade.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;

import com.jiseong.homesense.trade.dto.DealTypeFilter;
import com.jiseong.homesense.trade.dto.TradeDetailResponse;
import com.jiseong.homesense.trade.dto.TradeResponse;
import com.jiseong.homesense.trade.dto.TradeSearchCondition;
import com.jiseong.homesense.trade.dto.TradeSortCondition;
import com.jiseong.homesense.trade.dto.TradeSummaryResponse;
import com.jiseong.homesense.trade.entity.DealCategory;
import com.jiseong.homesense.trade.entity.HousingType;
import com.jiseong.homesense.trade.entity.Trade;
import com.jiseong.homesense.trade.exception.MissingComplexIdException;
import com.jiseong.homesense.trade.exception.TradeNotFoundException;
import com.jiseong.homesense.trade.repository.TradeRepository;

@ExtendWith(MockitoExtension.class)
class TradeServiceTest {

    @Mock
    private TradeRepository tradeRepository;

    private TradeService tradeService;

    @BeforeEach
    void setUp() {
        tradeService = new TradeService(tradeRepository);
    }

    private static Trade trade(Long id) {
        return Trade.builder()
                .tradeId(id)
                .housingType(HousingType.APT)
                .dealCategory(DealCategory.SALE)
                .datasetId("15126468")
                .sggCd("11680")
                .excluUseArea(new BigDecimal("84.90"))
                .dealDate(LocalDate.of(2026, 1, 10))
                .dealAmount(80000L)
                .cancelYn(false)
                .dedupHash("hash-" + id)
                .build();
    }

    @Test
    void search_리포지토리에_그대로_위임한다() {
        TradeSearchCondition condition = new TradeSearchCondition(
                null, null, null, null, null, null, null, null, TradeSortCondition.LATEST);
        Pageable pageable = PageRequest.of(0, 10);
        Page<TradeSummaryResponse> expected = new PageImpl<>(List.of());
        when(tradeRepository.search(condition, pageable)).thenReturn(expected);

        Page<TradeSummaryResponse> result = tradeService.search(condition, pageable);

        assertThat(result).isSameAs(expected);
    }

    @Test
    void getHistory_complexId가_NULL이면_MissingComplexIdException을_던진다() {
        assertThatThrownBy(() -> tradeService.getHistory(null, null, DealTypeFilter.ALL))
                .isInstanceOf(MissingComplexIdException.class);

        verify(tradeRepository, never()).findHistory(any(), any(), any());
    }

    @Test
    void getHistory_complexId가_있으면_리포지토리_결과를_TradeResponse로_매핑한다() {
        when(tradeRepository.findHistory(eq(1L), isNull(), eq(DealTypeFilter.ALL)))
                .thenReturn(List.of(trade(10L)));

        List<TradeResponse> result = tradeService.getHistory(1L, null, DealTypeFilter.ALL);

        assertThat(result).hasSize(1);
        assertThat(result.get(0).tradeId()).isEqualTo(10L);
    }

    @Test
    void getHistory_결과가_없으면_빈_리스트를_반환한다() {
        when(tradeRepository.findHistory(eq(1L), eq(HousingType.APT), eq(DealTypeFilter.JEONSE)))
                .thenReturn(List.of());

        List<TradeResponse> result = tradeService.getHistory(1L, HousingType.APT, DealTypeFilter.JEONSE);

        assertThat(result).isEmpty();
    }

    @Test
    void getHistory_취소된_거래도_isCancelled_플래그와_함께_그대로_포함한다() {
        Trade cancelled = Trade.builder()
                .tradeId(20L)
                .housingType(HousingType.APT)
                .dealCategory(DealCategory.SALE)
                .datasetId("15126468")
                .sggCd("11680")
                .excluUseArea(new BigDecimal("59.90"))
                .dealDate(LocalDate.of(2026, 1, 5))
                .dealAmount(50000L)
                .cancelYn(true)
                .dedupHash("hash-cancelled")
                .build();
        when(tradeRepository.findHistory(eq(1L), isNull(), eq(DealTypeFilter.ALL))).thenReturn(List.of(cancelled));

        List<TradeResponse> result = tradeService.getHistory(1L, null, DealTypeFilter.ALL);

        assertThat(result.get(0).isCancelled()).isTrue();
    }

    @Test
    void getHistory_registrationDate가_없으면_isRegistered가_false다() {
        when(tradeRepository.findHistory(eq(1L), isNull(), eq(DealTypeFilter.ALL))).thenReturn(List.of(trade(10L)));

        List<TradeResponse> result = tradeService.getHistory(1L, null, DealTypeFilter.ALL);

        assertThat(result.get(0).isRegistered()).isFalse();
    }

    @Test
    void getDetail_존재하지_않으면_TradeNotFoundException을_던진다() {
        when(tradeRepository.findById(999L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> tradeService.getDetail(999L)).isInstanceOf(TradeNotFoundException.class);
    }

    @Test
    void getDetail_존재하면_상세정보를_반환한다() {
        when(tradeRepository.findById(10L)).thenReturn(Optional.of(trade(10L)));

        TradeDetailResponse response = tradeService.getDetail(10L);

        assertThat(response.tradeId()).isEqualTo(10L);
        assertThat(response.dealAmount()).isEqualTo(80000L);
    }

    @Test
    void getDetail_aptDong이_NULL이면_aptDongPending이_true다() {
        when(tradeRepository.findById(10L)).thenReturn(Optional.of(trade(10L)));

        TradeDetailResponse response = tradeService.getDetail(10L);

        assertThat(response.aptDong()).isNull();
        assertThat(response.aptDongPending()).isTrue();
    }
}
