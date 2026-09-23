package com.jiseong.homesense.complex.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
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

import com.jiseong.homesense.common.exception.ComplexNotFoundException;
import com.jiseong.homesense.complex.dto.BoundsCondition;
import com.jiseong.homesense.complex.dto.ComplexDetailResponse;
import com.jiseong.homesense.complex.dto.ComplexMapSearchResponse;
import com.jiseong.homesense.complex.dto.ComplexSearchCondition;
import com.jiseong.homesense.complex.dto.ComplexSummaryResponse;
import com.jiseong.homesense.complex.dto.MapFilterCondition;
import com.jiseong.homesense.complex.entity.Complex;
import com.jiseong.homesense.complex.repository.ComplexRepository;
import com.jiseong.homesense.recentview.service.RecentViewService;
import com.jiseong.homesense.region.service.RegionCodePrefixResolver;
import com.jiseong.homesense.trade.entity.DealCategory;
import com.jiseong.homesense.trade.entity.HousingType;
import com.jiseong.homesense.trade.entity.MatchMethod;
import com.jiseong.homesense.trade.entity.Trade;
import com.jiseong.homesense.trade.repository.TradeRepository;

@ExtendWith(MockitoExtension.class)
class ComplexServiceTest {

    @Mock
    private ComplexRepository complexRepository;
    @Mock
    private TradeRepository tradeRepository;
    @Mock
    private ComplexDetailCache complexDetailCache;
    @Mock
    private RecentViewService recentViewService;
    @Mock
    private RegionCodePrefixResolver regionCodePrefixResolver;

    private ComplexService complexService;

    @BeforeEach
    void setUp() {
        complexService = new ComplexService(
                complexRepository, tradeRepository, complexDetailCache, recentViewService, regionCodePrefixResolver);
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

    private static Trade trade(Complex complex, LocalDate dealDate, long amount, String area) {
        return trade(complex, dealDate, amount, area, null, null);
    }

    private static Trade trade(Complex complex, LocalDate dealDate, long amount, String area,
            MatchMethod matchMethod, Short floor) {
        return Trade.builder()
                .housingType(HousingType.APT)
                .dealCategory(DealCategory.SALE)
                .datasetId("15126468")
                .sggCd("11680")
                .complex(complex)
                .excluUseArea(new BigDecimal(area))
                .dealDate(dealDate)
                .dealAmount(amount)
                .cancelYn(false)
                .matchMethod(matchMethod)
                .floor(floor)
                .dedupHash("hash-" + complex.getComplexId() + "-" + dealDate)
                .build();
    }

    @Test
    void search_regionCode가_없으면_prefix_없이_리포지토리에_위임한다() {
        ComplexSearchCondition condition = ComplexSearchCondition.builder().keyword("래미안").build();
        Pageable pageable = PageRequest.of(0, 10);
        Page<ComplexSummaryResponse> expected = new PageImpl<>(List.of());
        when(complexRepository.search(condition, null, pageable)).thenReturn(expected);

        Page<ComplexSummaryResponse> result = complexService.search(condition, pageable);

        assertThat(result).isSameAs(expected);
        verifyNoInteractions(regionCodePrefixResolver);
    }

    @Test
    void search_regionCode를_계층_prefix로_바꿔_리포지토리에_넘긴다() {
        ComplexSearchCondition condition = ComplexSearchCondition.builder().regionCode("4111000000").build();
        Pageable pageable = PageRequest.of(0, 10);
        when(regionCodePrefixResolver.prefixOf("4111000000")).thenReturn(Optional.of("4111"));
        when(complexRepository.search(condition, "4111", pageable)).thenReturn(new PageImpl<>(List.of()));

        complexService.search(condition, pageable);

        verify(complexRepository).search(condition, "4111", pageable);
    }

    @Test
    void search_존재하지_않거나_폐지된_regionCode면_조회하지_않고_빈_페이지를_돌려준다() {
        ComplexSearchCondition condition = ComplexSearchCondition.builder().regionCode("2811000000").build();
        Pageable pageable = PageRequest.of(0, 10);
        when(regionCodePrefixResolver.prefixOf("2811000000")).thenReturn(Optional.empty());

        Page<ComplexSummaryResponse> result = complexService.search(condition, pageable);

        assertThat(result.getContent()).isEmpty();
        assertThat(result.getTotalElements()).isZero();
        verifyNoInteractions(complexRepository);
    }

    @Test
    void getDetail_ComplexDetailCache가_던진_예외를_그대로_전파한다() {
        when(complexDetailCache.get(1L)).thenThrow(new ComplexNotFoundException());

        assertThatThrownBy(() -> complexService.getDetail(1L, null, null)).isInstanceOf(ComplexNotFoundException.class);
    }

    @Test
    void getDetail_ComplexDetailCache의_결과를_그대로_반환한다() {
        ComplexDetailResponse cached = detailResponse(1L, HousingType.APT);
        when(complexDetailCache.get(1L)).thenReturn(cached);

        ComplexDetailResponse response = complexService.getDetail(1L, null, null);

        assertThat(response).isSameAs(cached);
    }

    /**
     * 설계서 3.6절("RCV 도메인과 협력") 그대로 SVC-CPX-01이 SVC-RCV-01.record()를 직접 호출하는지
     * 검증한다 — 캐시 히트/미스와 무관하게 매 호출마다 실행돼야 하므로, 캐시 조회 자체는
     * ComplexDetailCache로 분리해 이 메서드가 항상 record()를 부르게 했다(CLAUDE.md SVC-RCV-01
     * 절 참고).
     */
    @Test
    void getDetail_SVC_RCV_01_record를_호출해_조회이력을_남긴다() {
        ComplexDetailResponse cached = detailResponse(1L, HousingType.APT);
        when(complexDetailCache.get(1L)).thenReturn(cached);

        complexService.getDetail(1L, 5L, "session-x");

        verify(recentViewService).record(eq(5L), eq("session-x"),
                argThat(target -> target.complexId().equals(1L) && target.housingType() == HousingType.APT));
    }

    private static ComplexDetailResponse detailResponse(Long complexId, HousingType housingType) {
        ComplexDetailResponse.BasicInfo basicInfo = new ComplexDetailResponse.BasicInfo(null, null, null, null, null, null);
        ComplexDetailResponse.ExtendedInfo extendedInfo = new ComplexDetailResponse.ExtendedInfo(
                null, null, null, null, null, null, null, null, null, null, null,
                (short) 0, (short) 0, (short) 0, null, null, null, null, null, null, null, null,
                null, null, null, null, null, null);
        return new ComplexDetailResponse(complexId, "테스트단지" + complexId, "아파트", housingType,
                null, null, null, null, null, null, null, false, basicInfo, extendedInfo);
    }

    @Test
    void getPopular_거래량_상위_단지를_대표거래와_함께_반환한다() {
        // limit(1)만큼 거래량 기준 결과가 이미 충분해 fallback 경로를 타지 않는다.
        Complex complex1 = complex(1L);
        Trade representativeTrade = trade(complex1, LocalDate.of(2026, 1, 10), 50000L, "84.99");
        when(tradeRepository.findTopComplexIdsByRecentTradeVolume(any(), any())).thenReturn(List.of(1L));
        when(complexRepository.findAllById(List.of(1L))).thenReturn(List.of(complex1));
        when(tradeRepository.findRecentTradesByComplexIds(List.of(1L))).thenReturn(Map.of(1L, representativeTrade));

        List<ComplexSummaryResponse> result = complexService.getPopular(1);

        assertThat(result).hasSize(1);
        assertThat(result.get(0).complexId()).isEqualTo(1L);
        assertThat(result.get(0).representativeAmount()).isEqualTo(50000L);
        verify(complexRepository, never()).findAllByOrderByComplexIdDesc(any());
    }

    @Test
    void getPopular_대표거래의_matchMethod와_floor를_그대로_응답에_담는다() {
        Complex complex1 = complex(1L);
        Trade representativeTrade = trade(complex1, LocalDate.of(2026, 1, 10), 50000L, "84.99",
                MatchMethod.SIMILAR, (short) 7);
        when(tradeRepository.findTopComplexIdsByRecentTradeVolume(any(), any())).thenReturn(List.of(1L));
        when(complexRepository.findAllById(List.of(1L))).thenReturn(List.of(complex1));
        when(tradeRepository.findRecentTradesByComplexIds(List.of(1L))).thenReturn(Map.of(1L, representativeTrade));

        List<ComplexSummaryResponse> result = complexService.getPopular(1);

        assertThat(result.get(0).matchMethod()).isEqualTo(MatchMethod.SIMILAR);
        assertThat(result.get(0).floor()).isEqualTo((short) 7);
    }

    @Test
    void getPopular_대표거래가_없는_단지는_결과에서_제외한다() {
        when(tradeRepository.findTopComplexIdsByRecentTradeVolume(any(), any())).thenReturn(List.of(1L));
        when(complexRepository.findAllById(List.of(1L))).thenReturn(List.of(complex(1L)));
        when(tradeRepository.findRecentTradesByComplexIds(List.of(1L))).thenReturn(Map.of());

        List<ComplexSummaryResponse> result = complexService.getPopular(1);

        assertThat(result).isEmpty();
    }

    @Test
    void getPopular_거래량_기준이_limit에_못_미치면_최근_등록_단지로_나머지를_채운다() {
        Complex byVolume = complex(1L);
        Complex fallback = complex(2L);
        Trade volumeTrade = trade(byVolume, LocalDate.of(2026, 1, 10), 50000L, "84.99");
        Trade fallbackTrade = trade(fallback, LocalDate.of(2025, 6, 1), 30000L, "59.90");

        when(tradeRepository.findTopComplexIdsByRecentTradeVolume(any(), any())).thenReturn(List.of(1L));
        when(complexRepository.findAllByOrderByComplexIdDesc(any())).thenReturn(List.of(fallback));
        when(complexRepository.findAllById(List.of(1L, 2L))).thenReturn(List.of(byVolume, fallback));
        when(tradeRepository.findRecentTradesByComplexIds(List.of(1L, 2L)))
                .thenReturn(Map.of(1L, volumeTrade, 2L, fallbackTrade));

        List<ComplexSummaryResponse> result = complexService.getPopular(2);

        assertThat(result).extracting(ComplexSummaryResponse::complexId).containsExactly(1L, 2L);
    }

    @Test
    void getPopular_fallback_후보에_이미_거래량_기준으로_뽑힌_단지가_있으면_제외한다() {
        Complex byVolume = complex(1L);
        Trade volumeTrade = trade(byVolume, LocalDate.of(2026, 1, 10), 50000L, "84.99");

        when(tradeRepository.findTopComplexIdsByRecentTradeVolume(any(), any())).thenReturn(List.of(1L));
        // fallback 후보 목록에 이미 거래량 기준으로 뽑힌 1L이 다시 나타난다(예: 그 단지의
        // complex_id도 등록 순서상 최근인 경우) — 결과에 중복으로 나오면 안 된다.
        when(complexRepository.findAllByOrderByComplexIdDesc(any())).thenReturn(List.of(byVolume));
        when(complexRepository.findAllById(List.of(1L))).thenReturn(List.of(byVolume));
        when(tradeRepository.findRecentTradesByComplexIds(List.of(1L))).thenReturn(Map.of(1L, volumeTrade));

        List<ComplexSummaryResponse> result = complexService.getPopular(2);

        assertThat(result).extracting(ComplexSummaryResponse::complexId).containsExactly(1L);
    }

    @Test
    void searchInBounds_상한_이하면_truncated가_false다() {
        MapFilterCondition filter = new MapFilterCondition(null);
        BoundsCondition bounds = new BoundsCondition(
                new BigDecimal("37.0"), new BigDecimal("127.0"), new BigDecimal("37.1"), new BigDecimal("127.1"));
        when(complexRepository.searchInBounds(any(), any(), anyInt())).thenReturn(List.of(complex(1L), complex(2L)));

        ComplexMapSearchResponse response = complexService.searchInBounds(bounds, filter);

        assertThat(response.truncated()).isFalse();
        assertThat(response.points()).hasSize(2);
    }

    @Test
    void searchInBounds_상한을_넘으면_상한까지만_반환하고_truncated가_true다() {
        MapFilterCondition filter = new MapFilterCondition(null);
        BoundsCondition bounds = new BoundsCondition(
                new BigDecimal("37.0"), new BigDecimal("127.0"), new BigDecimal("37.1"), new BigDecimal("127.1"));
        List<Complex> overLimit = java.util.stream.LongStream.rangeClosed(1, 501)
                .mapToObj(ComplexServiceTest::complex)
                .toList();
        when(complexRepository.searchInBounds(any(), any(), anyInt())).thenReturn(overLimit);

        ComplexMapSearchResponse response = complexService.searchInBounds(bounds, filter);

        assertThat(response.truncated()).isTrue();
        assertThat(response.points()).hasSize(500);
    }
}
