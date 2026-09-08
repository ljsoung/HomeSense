package com.jiseong.homesense.region.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.Optional;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.jiseong.homesense.region.dto.RegionStats;
import com.jiseong.homesense.trade.repository.TradeRepository;

@ExtendWith(MockitoExtension.class)
class RegionStatsCalculatorTest {

    private static final ZoneId KST = ZoneId.of("Asia/Seoul");

    @Mock
    private TradeRepository tradeRepository;

    private RegionStatsCalculator calculator;

    @BeforeEach
    void setUp() {
        calculator = new RegionStatsCalculator(tradeRepository);
    }

    @Test
    void 이번달과_지난달_평균가가_모두_있으면_변동률을_계산한다() {
        LocalDate now = LocalDate.now(KST);
        when(tradeRepository.findAverageSaleAmount(eq("1168010100"), eq(now.minusMonths(1)), any()))
                .thenReturn(Optional.of(110_000.0));
        when(tradeRepository.findAverageSaleAmount(eq("1168010100"), eq(now.minusMonths(2)), eq(now.minusMonths(1))))
                .thenReturn(Optional.of(100_000.0));

        RegionStats stats = calculator.calculate("1168010100");

        assertThat(stats.avgPrice()).isEqualByComparingTo(new BigDecimal("110000"));
        assertThat(stats.changeRate()).isEqualByComparingTo(new BigDecimal("10.00"));
    }

    @Test
    void 지난달_거래가_없으면_변동률은_null이다() {
        when(tradeRepository.findAverageSaleAmount(any(), any(), any())).thenReturn(Optional.empty());

        RegionStats stats = calculator.calculate("1168010100");

        assertThat(stats.avgPrice()).isNull();
        assertThat(stats.changeRate()).isNull();
    }

    @Test
    void 이번달_거래는_있고_지난달_거래가_없으면_평균가만_채워지고_변동률은_null이다() {
        LocalDate now = LocalDate.now(KST);
        when(tradeRepository.findAverageSaleAmount(eq("1168010100"), eq(now.minusMonths(1)), any()))
                .thenReturn(Optional.of(50_000.0));
        when(tradeRepository.findAverageSaleAmount(eq("1168010100"), eq(now.minusMonths(2)), eq(now.minusMonths(1))))
                .thenReturn(Optional.empty());

        RegionStats stats = calculator.calculate("1168010100");

        assertThat(stats.avgPrice()).isEqualByComparingTo(new BigDecimal("50000"));
        assertThat(stats.changeRate()).isNull();
    }
}
