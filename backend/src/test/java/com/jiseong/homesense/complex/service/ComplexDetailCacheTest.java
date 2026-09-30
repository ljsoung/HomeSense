package com.jiseong.homesense.complex.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.when;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.jiseong.homesense.common.exception.ComplexNotFoundException;
import com.jiseong.homesense.complex.dto.ComplexDetailResponse;
import com.jiseong.homesense.complex.entity.Complex;
import com.jiseong.homesense.complex.repository.ComplexRepository;
import com.jiseong.homesense.region.entity.LegalDistrictCode;
import com.jiseong.homesense.trade.entity.MatchMethod;
import com.jiseong.homesense.trade.entity.Trade;
import com.jiseong.homesense.trade.repository.TradeRepository;

@ExtendWith(MockitoExtension.class)
class ComplexDetailCacheTest {

    @Mock
    private ComplexRepository complexRepository;

    @Mock
    private TradeRepository tradeRepository;

    private ComplexDetailCache complexDetailCache;

    @BeforeEach
    void setUp() {
        complexDetailCache = new ComplexDetailCache(complexRepository, tradeRepository);
    }

    private static Complex complex(Long id) {
        return Complex.builder()
                .complexId(id)
                .sourceComplexCd("SRC-" + id)
                .complexName("테스트단지" + id)
                .complexType("아파트")
                .elevatorPassengerCount((short) 1)
                .elevatorCargoCount((short) 0)
                .elevatorCombinedCount((short) 0)
                .dataUpdatedAt(LocalDate.of(2026, 1, 1))
                .build();
    }

    @Test
    void get_존재하지_않으면_ComplexNotFoundException을_던진다() {
        when(complexRepository.findById(1L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> complexDetailCache.get(1L)).isInstanceOf(ComplexNotFoundException.class);
    }

    @Test
    void get_존재하면_상세정보를_반환한다() {
        when(complexRepository.findById(1L)).thenReturn(Optional.of(complex(1L)));

        ComplexDetailResponse response = complexDetailCache.get(1L);

        assertThat(response.complexId()).isEqualTo(1L);
        assertThat(response.complexName()).isEqualTo("테스트단지1");
    }

    @Test
    void get_법정동_매칭_대기면_matchPending이_true다() {
        when(complexRepository.findById(1L)).thenReturn(Optional.of(complex(1L)));

        ComplexDetailResponse response = complexDetailCache.get(1L);

        assertThat(response.matchPending()).isTrue();
        assertThat(response.legalDongCd()).isNull();
    }

    @Test
    void get_법정동이_매칭되면_legalDongCd를_담는다() {
        Complex matched = Complex.builder()
                .complexId(2L)
                .sourceComplexCd("SRC-2")
                .complexName("테스트단지2")
                .legalDistrictCode(LegalDistrictCode.builder().legalDongCd("4155025021").build())
                .dataUpdatedAt(LocalDate.of(2026, 1, 1))
                .build();
        when(complexRepository.findById(2L)).thenReturn(Optional.of(matched));

        ComplexDetailResponse response = complexDetailCache.get(2L);

        assertThat(response.matchPending()).isFalse();
        assertThat(response.legalDongCd()).isEqualTo("4155025021");
    }

    @Test
    void get_대표_거래의_matchMethod를_정밀도로_담는다() {
        when(complexRepository.findById(1L)).thenReturn(Optional.of(complex(1L)));
        Trade representative = Trade.builder().tradeId(10L).matchMethod(MatchMethod.SIMILAR).build();
        when(tradeRepository.findRecentTradesByComplexIds(List.of(1L))).thenReturn(Map.of(1L, representative));

        ComplexDetailResponse response = complexDetailCache.get(1L);

        assertThat(response.matchMethod()).isEqualTo(MatchMethod.SIMILAR);
    }

    @Test
    void get_대표_거래가_없으면_matchMethod는_null이다() {
        when(complexRepository.findById(1L)).thenReturn(Optional.of(complex(1L)));
        when(tradeRepository.findRecentTradesByComplexIds(List.of(1L))).thenReturn(Map.of());

        ComplexDetailResponse response = complexDetailCache.get(1L);

        assertThat(response.matchMethod()).isNull();
    }
}
