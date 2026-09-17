package com.jiseong.homesense.recentview.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Pageable;

import com.jiseong.homesense.complex.dto.ComplexSummaryResponse;
import com.jiseong.homesense.complex.entity.Complex;
import com.jiseong.homesense.complex.repository.ComplexRepository;
import com.jiseong.homesense.recentview.dto.RecentViewResponse;
import com.jiseong.homesense.recentview.dto.RecentViewTarget;
import com.jiseong.homesense.recentview.entity.RecentView;
import com.jiseong.homesense.recentview.repository.RecentViewRepository;
import com.jiseong.homesense.trade.entity.DealCategory;
import com.jiseong.homesense.trade.entity.HousingType;
import com.jiseong.homesense.trade.entity.Trade;
import com.jiseong.homesense.trade.repository.TradeRepository;
import com.jiseong.homesense.user.entity.User;
import com.jiseong.homesense.user.repository.UserRepository;

@ExtendWith(MockitoExtension.class)
class RecentViewServiceTest {

    @Mock
    private RecentViewRepository recentViewRepository;
    @Mock
    private UserRepository userRepository;
    @Mock
    private ComplexRepository complexRepository;
    @Mock
    private TradeRepository tradeRepository;

    private RecentViewService recentViewService;

    @BeforeEach
    void setUp() {
        recentViewService =
                new RecentViewService(recentViewRepository, userRepository, complexRepository, tradeRepository);
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
                .build();
    }

    private static Complex complexWithAddress(Long id, String sido, String sigungu, String dongRi) {
        return Complex.builder()
                .complexId(id)
                .sourceComplexCd("SRC-" + id)
                .complexName("테스트단지" + id)
                .complexType("아파트")
                .sido(sido)
                .sigungu(sigungu)
                .dongRi(dongRi)
                .elevatorPassengerCount((short) 1)
                .elevatorCargoCount((short) 0)
                .elevatorCombinedCount((short) 0)
                .build();
    }

    private static Trade saleTrade(Long dealAmount, String area, Short floor) {
        return Trade.builder()
                .housingType(HousingType.APT)
                .dealCategory(DealCategory.SALE)
                .datasetId("15126468")
                .sggCd("11680")
                .excluUseArea(new BigDecimal(area))
                .dealDate(LocalDate.of(2026, 1, 10))
                .dealAmount(dealAmount)
                .floor(floor)
                .cancelYn(false)
                .dedupHash("hash-sale-" + dealAmount + "-" + area)
                .build();
    }

    private static Trade rentTrade(Long depositAmount, String area, Short floor) {
        return Trade.builder()
                .housingType(HousingType.APT)
                .dealCategory(DealCategory.RENT)
                .datasetId("15126474")
                .sggCd("11680")
                .excluUseArea(new BigDecimal(area))
                .dealDate(LocalDate.of(2026, 1, 10))
                .depositAmount(depositAmount)
                .floor(floor)
                .cancelYn(false)
                .dedupHash("hash-rent-" + depositAmount + "-" + area)
                .build();
    }

    @Test
    void getRecent_userId와_sessionId가_모두_없으면_빈_리스트를_반환한다() {
        List<RecentViewResponse> result = recentViewService.getRecent(null, null, 3);

        assertThat(result).isEmpty();
    }

    @Test
    void getRecent_userId가_있으면_회원_기준으로_조회한다() {
        RecentView view = RecentView.record(null, "session-x", complex(1L), HousingType.APT);
        when(recentViewRepository.findByUser_UserIdOrderByViewedAtDesc(eq(1L), any(Pageable.class)))
                .thenReturn(List.of(view));
        when(tradeRepository.findRecentTradesByComplexIds(any())).thenReturn(Map.of());

        List<RecentViewResponse> result = recentViewService.getRecent(1L, null, 3);

        assertThat(result).hasSize(1);
        assertThat(result.get(0).complexId()).isEqualTo(1L);
    }

    /**
     * sido/sigungu/dongRi는 ComplexSummaryResponse와 동일한 원시 필드 3개를 그대로 노출한다(CLAUDE.md
     * CPX-RCV-RGN 카드 표시 필드 보강 작업 참고) — 조합·가공 없이 Complex 엔티티 값을 그대로 통과시킨다.
     */
    @Test
    void getRecent_단지의_sido_sigungu_dongRi를_그대로_응답에_담는다() {
        Complex complexWithAddress = complexWithAddress(1L, "서울특별시", "강남구", "역삼동");
        RecentView view = RecentView.record(null, "session-x", complexWithAddress, HousingType.APT);
        when(recentViewRepository.findBySessionIdOrderByViewedAtDesc(eq("session-x"), any(Pageable.class)))
                .thenReturn(List.of(view));
        when(tradeRepository.findRecentTradesByComplexIds(any())).thenReturn(Map.of());

        List<RecentViewResponse> result = recentViewService.getRecent(null, "session-x", 3);

        assertThat(result.get(0).sido()).isEqualTo("서울특별시");
        assertThat(result.get(0).sigungu()).isEqualTo("강남구");
        assertThat(result.get(0).dongRi()).isEqualTo("역삼동");
    }

    /** 단지 기본정보 xlsx 원본에 주소 컬럼이 미기재된 경우 — 지어내지 않고 NULL을 그대로 노출한다. */
    @Test
    void getRecent_단지의_주소_컬럼이_NULL이면_그대로_NULL을_응답에_담는다() {
        RecentView view = RecentView.record(null, "session-x", complex(1L), HousingType.APT);
        when(recentViewRepository.findBySessionIdOrderByViewedAtDesc(eq("session-x"), any(Pageable.class)))
                .thenReturn(List.of(view));
        when(tradeRepository.findRecentTradesByComplexIds(any())).thenReturn(Map.of());

        List<RecentViewResponse> result = recentViewService.getRecent(null, "session-x", 3);

        assertThat(result.get(0).sido()).isNull();
        assertThat(result.get(0).sigungu()).isNull();
        assertThat(result.get(0).dongRi()).isNull();
    }

    @Test
    void getRecent_userId가_없으면_sessionId_기준으로_조회한다() {
        RecentView view = RecentView.record(null, "session-x", complex(2L), HousingType.VILLA);
        when(recentViewRepository.findBySessionIdOrderByViewedAtDesc(eq("session-x"), any(Pageable.class)))
                .thenReturn(List.of(view));
        when(tradeRepository.findRecentTradesByComplexIds(any())).thenReturn(Map.of());

        List<RecentViewResponse> result = recentViewService.getRecent(null, "session-x", 3);

        assertThat(result).hasSize(1);
        assertThat(result.get(0).complexId()).isEqualTo(2L);
    }

    @Test
    void getRecent_대표거래가_있으면_SALE의_dealAmount를_price로_담는다() {
        Complex complexEntity = complex(1L);
        Trade representativeTrade = saleTrade(50000L, "84.99", (short) 12);
        RecentView view = RecentView.record(null, "session-x", complexEntity, HousingType.APT);
        when(recentViewRepository.findBySessionIdOrderByViewedAtDesc(eq("session-x"), any(Pageable.class)))
                .thenReturn(List.of(view));
        when(tradeRepository.findRecentTradesByComplexIds(List.of(1L))).thenReturn(Map.of(1L, representativeTrade));

        List<RecentViewResponse> result = recentViewService.getRecent(null, "session-x", 3);

        assertThat(result.get(0).price()).isEqualTo(50000L);
        assertThat(result.get(0).area()).isEqualByComparingTo("84.99");
        assertThat(result.get(0).floor()).isEqualTo((short) 12);
    }

    @Test
    void getRecent_대표거래가_RENT면_depositAmount를_price로_담는다() {
        Complex complexEntity = complex(1L);
        Trade representativeTrade = rentTrade(30000L, "59.90", (short) 3);
        RecentView view = RecentView.record(null, "session-x", complexEntity, HousingType.APT);
        when(recentViewRepository.findBySessionIdOrderByViewedAtDesc(eq("session-x"), any(Pageable.class)))
                .thenReturn(List.of(view));
        when(tradeRepository.findRecentTradesByComplexIds(List.of(1L))).thenReturn(Map.of(1L, representativeTrade));

        List<RecentViewResponse> result = recentViewService.getRecent(null, "session-x", 3);

        assertThat(result.get(0).price()).isEqualTo(30000L);
    }

    @Test
    void getRecent_floor가_NULL인_대표거래는_floor를_NULL로_담는다() {
        Complex complexEntity = complex(1L);
        Trade representativeTrade = saleTrade(50000L, "84.99", null);
        RecentView view = RecentView.record(null, "session-x", complexEntity, HousingType.APT);
        when(recentViewRepository.findBySessionIdOrderByViewedAtDesc(eq("session-x"), any(Pageable.class)))
                .thenReturn(List.of(view));
        when(tradeRepository.findRecentTradesByComplexIds(List.of(1L))).thenReturn(Map.of(1L, representativeTrade));

        List<RecentViewResponse> result = recentViewService.getRecent(null, "session-x", 3);

        assertThat(result.get(0).floor()).isNull();
    }

    /**
     * 대표 거래 자체가 없는 경우(이론상 recent_view가 가리키는 단지에 취소되지 않은 거래가 없는
     * 경우) — 예외를 던지지 않고 price/area/floor를 모두 null로 반환한다(완료 조건의 예외 처리표).
     */
    @Test
    void getRecent_대표거래가_없으면_price_area_floor_모두_NULL이다() {
        RecentView view = RecentView.record(null, "session-x", complex(1L), HousingType.APT);
        when(recentViewRepository.findBySessionIdOrderByViewedAtDesc(eq("session-x"), any(Pageable.class)))
                .thenReturn(List.of(view));
        when(tradeRepository.findRecentTradesByComplexIds(List.of(1L))).thenReturn(Map.of());

        List<RecentViewResponse> result = recentViewService.getRecent(null, "session-x", 3);

        assertThat(result.get(0).price()).isNull();
        assertThat(result.get(0).area()).isNull();
        assertThat(result.get(0).floor()).isNull();
    }

    /**
     * "같은 단지가 인기 단지 카드와 최근 조회 카드에서 다른 가격/층을 보여주면 안 된다"는 이 작업의
     * 핵심 요구사항(CLAUDE.md "CPX-RCV-RGN price/area/floor 보강" 절 참고)을 고정한다 — 두 DTO가
     * 정확히 같은 Trade 인스턴스에서 항상 같은 price/area/floor를 뽑아내는지 직접 비교한다. 이 두
     * DTO의 amount 분기 삼항식 중 한쪽만 수정되고 다른 쪽은 안 바뀌는 회귀를 잡아낸다.
     */
    @Test
    void getRecent_가격_계산_분기가_ComplexSummaryResponse와_동일하다() {
        Complex complexEntity = complex(1L);
        Trade representativeTrade = rentTrade(45000L, "59.90", (short) 8);
        RecentView view = RecentView.record(null, "session-x", complexEntity, HousingType.APT);
        when(recentViewRepository.findBySessionIdOrderByViewedAtDesc(eq("session-x"), any(Pageable.class)))
                .thenReturn(List.of(view));
        when(tradeRepository.findRecentTradesByComplexIds(List.of(1L))).thenReturn(Map.of(1L, representativeTrade));

        List<RecentViewResponse> result = recentViewService.getRecent(null, "session-x", 3);
        ComplexSummaryResponse cpxEquivalent = ComplexSummaryResponse.of(complexEntity, representativeTrade);

        assertThat(result.get(0).price()).isEqualTo(cpxEquivalent.representativeAmount());
        assertThat(result.get(0).area()).isEqualByComparingTo(cpxEquivalent.representativeArea());
        assertThat(result.get(0).floor()).isEqualTo(cpxEquivalent.floor());
    }

    @Test
    void record_userId와_sessionId가_모두_없으면_조용히_반환한다() {
        recentViewService.record(null, null, new RecentViewTarget(1L, HousingType.APT));

        verify(recentViewRepository, never()).save(any());
    }

    /**
     * complex_type 원본 미기재(NULL, 약 0.48%)인 단지는 Complex.inferHousingType()이 null을
     * 돌려준다 — recent_view.housing_type이 NOT NULL이라 이 경우 잘못된 값을 추정해 저장하는
     * 대신 조용히 기록을 스킵한다(CLAUDE.md SVC-RCV-01 절 참고).
     */
    @Test
    void record_target의_housingType이_null이면_조용히_반환한다() {
        recentViewService.record(1L, null, new RecentViewTarget(10L, null));

        verify(recentViewRepository, never()).save(any());
        verify(recentViewRepository, never()).findByUser_UserIdAndComplex_ComplexId(any(), any());
    }

    @Test
    void record_기존_레코드가_있으면_viewedAt만_갱신하고_신규_저장하지_않는다() {
        RecentView existing = RecentView.record(null, "session-x", complex(1L), HousingType.APT);
        LocalDateTime originalViewedAt = existing.getViewedAt();
        when(recentViewRepository.findBySessionIdAndComplex_ComplexId("session-x", 1L))
                .thenReturn(Optional.of(existing));

        recentViewService.record(null, "session-x", new RecentViewTarget(1L, HousingType.APT));

        verify(recentViewRepository, never()).save(any());
        assertThat(existing.getViewedAt()).isAfterOrEqualTo(originalViewedAt);
    }

    @Test
    void record_기존_레코드가_없으면_신규로_저장한다() {
        when(recentViewRepository.findByUser_UserIdAndComplex_ComplexId(1L, 10L)).thenReturn(Optional.empty());
        when(userRepository.getReferenceById(1L)).thenReturn(User.builder().build());
        when(complexRepository.getReferenceById(10L)).thenReturn(complex(10L));
        when(recentViewRepository.countByUser_UserId(1L)).thenReturn(1L);

        recentViewService.record(1L, null, new RecentViewTarget(10L, HousingType.APT));

        ArgumentCaptor<RecentView> captor = ArgumentCaptor.forClass(RecentView.class);
        verify(recentViewRepository, times(1)).save(captor.capture());
        assertThat(captor.getValue().getComplex().getComplexId()).isEqualTo(10L);
        assertThat(captor.getValue().getHousingType()).isEqualTo(HousingType.APT);
    }

    /**
     * 인증된 브라우저가 기존에 갖고 있던 X-Session-Id를 함께 보내는 경우, actor는 userId로
     * 결정되지만 저장 시 sessionId까지 같이 남기면 이후 그 세션ID로 오는 비로그인 요청이
     * 로그인 상태에서 쌓인 이력을 그대로 조회·갱신할 수 있다(Codex 코드리뷰 P1 지적,
     * CLAUDE.md SVC-RCV-01 절 참고) — 회귀 방지.
     */
    @Test
    void record_userId와_sessionId가_모두_있으면_sessionId는_저장하지_않는다() {
        when(recentViewRepository.findByUser_UserIdAndComplex_ComplexId(1L, 10L)).thenReturn(Optional.empty());
        when(userRepository.getReferenceById(1L)).thenReturn(User.builder().build());
        when(complexRepository.getReferenceById(10L)).thenReturn(complex(10L));
        when(recentViewRepository.countByUser_UserId(1L)).thenReturn(1L);

        recentViewService.record(1L, "session-x", new RecentViewTarget(10L, HousingType.APT));

        ArgumentCaptor<RecentView> captor = ArgumentCaptor.forClass(RecentView.class);
        verify(recentViewRepository, times(1)).save(captor.capture());
        assertThat(captor.getValue().getSessionId()).isNull();
        verify(recentViewRepository, never()).findBySessionIdAndComplex_ComplexId(any(), any());
    }

    @Test
    void record_보관건수_상한을_넘으면_가장_오래된_레코드부터_삭제한다() {
        when(recentViewRepository.findBySessionIdAndComplex_ComplexId("session-x", 10L)).thenReturn(Optional.empty());
        when(complexRepository.getReferenceById(10L)).thenReturn(complex(10L));
        when(recentViewRepository.countBySessionId("session-x")).thenReturn(21L);
        RecentView oldest = RecentView.record(null, "session-x", complex(999L), HousingType.APT);
        when(recentViewRepository.findBySessionIdOrderByViewedAtAsc(eq("session-x"), any(Pageable.class)))
                .thenReturn(List.of(oldest));

        recentViewService.record(null, "session-x", new RecentViewTarget(10L, HousingType.APT));

        verify(recentViewRepository).deleteAll(List.of(oldest));
    }

    @Test
    void record_보관건수가_상한_이하면_삭제하지_않는다() {
        when(recentViewRepository.findByUser_UserIdAndComplex_ComplexId(1L, 10L)).thenReturn(Optional.empty());
        when(userRepository.getReferenceById(1L)).thenReturn(User.builder().build());
        when(complexRepository.getReferenceById(10L)).thenReturn(complex(10L));
        when(recentViewRepository.countByUser_UserId(anyLong())).thenReturn(5L);

        recentViewService.record(1L, null, new RecentViewTarget(10L, HousingType.APT));

        verify(recentViewRepository, never()).deleteAll(any());
    }
}
