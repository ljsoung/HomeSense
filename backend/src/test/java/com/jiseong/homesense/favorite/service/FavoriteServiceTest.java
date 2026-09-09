package com.jiseong.homesense.favorite.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.DataIntegrityViolationException;

import com.jiseong.homesense.common.exception.ComplexNotFoundException;
import com.jiseong.homesense.complex.entity.Complex;
import com.jiseong.homesense.complex.repository.ComplexRepository;
import com.jiseong.homesense.favorite.dto.AddFavoritePropertyCommand;
import com.jiseong.homesense.favorite.dto.AddFavoriteRegionCommand;
import com.jiseong.homesense.favorite.dto.FavoritePropertyResponse;
import com.jiseong.homesense.favorite.dto.FavoritePropertySummaryResponse;
import com.jiseong.homesense.favorite.dto.FavoriteRegionResponse;
import com.jiseong.homesense.favorite.dto.FavoriteRegionSummaryResponse;
import com.jiseong.homesense.favorite.entity.FavoriteProperty;
import com.jiseong.homesense.favorite.entity.FavoriteRegion;
import com.jiseong.homesense.favorite.exception.AccessDeniedException;
import com.jiseong.homesense.favorite.exception.DuplicateFavoriteException;
import com.jiseong.homesense.favorite.exception.DuplicateFavoriteRegionException;
import com.jiseong.homesense.favorite.exception.FavoriteNotFoundException;
import com.jiseong.homesense.favorite.exception.HousingTypeUndeterminedException;
import com.jiseong.homesense.favorite.exception.MissingComplexIdException;
import com.jiseong.homesense.favorite.repository.FavoritePropertyRepository;
import com.jiseong.homesense.favorite.repository.FavoriteRegionRepository;
import com.jiseong.homesense.notification.repository.NotificationSettingRepository;
import com.jiseong.homesense.region.dto.RegionStats;
import com.jiseong.homesense.region.entity.LegalDistrictCode;
import com.jiseong.homesense.region.exception.RegionNotFoundException;
import com.jiseong.homesense.region.repository.LegalDistrictCodeRepository;
import com.jiseong.homesense.region.service.RegionStatsCalculator;
import com.jiseong.homesense.trade.entity.DealCategory;
import com.jiseong.homesense.trade.entity.HousingType;
import com.jiseong.homesense.trade.entity.Trade;
import com.jiseong.homesense.trade.repository.TradeRepository;
import com.jiseong.homesense.user.entity.User;
import com.jiseong.homesense.user.repository.UserRepository;

@ExtendWith(MockitoExtension.class)
class FavoriteServiceTest {

    private static final ZoneId KST = ZoneId.of("Asia/Seoul");

    @Mock
    private FavoritePropertyRepository favoritePropertyRepository;
    @Mock
    private FavoriteRegionRepository favoriteRegionRepository;
    @Mock
    private ComplexRepository complexRepository;
    @Mock
    private LegalDistrictCodeRepository legalDistrictCodeRepository;
    @Mock
    private TradeRepository tradeRepository;
    @Mock
    private NotificationSettingRepository notificationSettingRepository;
    @Mock
    private UserRepository userRepository;
    @Mock
    private RegionStatsCalculator regionStatsCalculator;

    private FavoriteService favoriteService;

    @BeforeEach
    void setUp() {
        favoriteService = new FavoriteService(favoritePropertyRepository, favoriteRegionRepository,
                complexRepository, legalDistrictCodeRepository, tradeRepository, notificationSettingRepository,
                userRepository, regionStatsCalculator);
    }

    private static Complex complex(Long id, String complexType) {
        return Complex.builder()
                .complexId(id)
                .sourceComplexCd("SRC-" + id)
                .complexName("테스트단지" + id)
                .complexType(complexType)
                .elevatorPassengerCount((short) 1)
                .elevatorCargoCount((short) 0)
                .elevatorCombinedCount((short) 0)
                .build();
    }

    private static LegalDistrictCode region(String legalDongCd) {
        return LegalDistrictCode.builder()
                .legalDongCd(legalDongCd)
                .legalDongName("서울특별시 강남구 역삼동")
                .sidoName("서울특별시")
                .sigunguName("강남구")
                .eupmyeondongName("역삼동")
                .isActive(true)
                .dataVersion(LocalDate.of(2026, 1, 1))
                .build();
    }

    // ---- addFavoriteProperty ----

    @Test
    void addFavoriteProperty_complexId가_없으면_MissingComplexIdException을_던진다() {
        assertThatThrownBy(() -> favoriteService.addFavoriteProperty(1L, new AddFavoritePropertyCommand(null)))
                .isInstanceOf(MissingComplexIdException.class);
        verify(favoritePropertyRepository, never()).save(any());
    }

    @Test
    void addFavoriteProperty_이미_등록된_조합이면_DuplicateFavoriteException을_던진다() {
        when(favoritePropertyRepository.existsByUser_UserIdAndComplex_ComplexId(1L, 10L)).thenReturn(true);

        assertThatThrownBy(() -> favoriteService.addFavoriteProperty(1L, new AddFavoritePropertyCommand(10L)))
                .isInstanceOf(DuplicateFavoriteException.class);
        verify(complexRepository, never()).findById(any());
    }

    @Test
    void addFavoriteProperty_단지가_존재하지_않으면_ComplexNotFoundException을_던진다() {
        when(favoritePropertyRepository.existsByUser_UserIdAndComplex_ComplexId(1L, 10L)).thenReturn(false);
        when(complexRepository.findById(10L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> favoriteService.addFavoriteProperty(1L, new AddFavoritePropertyCommand(10L)))
                .isInstanceOf(ComplexNotFoundException.class);
    }

    /** complex_type 원본 미기재(NULL, 약 0.48%)인 단지는 등록 자체를 막는다 — CLAUDE.md SVC-RCV-01 절의 결정과 대비되는 지점(동기 쓰기라 스킵할 수 없음). */
    @Test
    void addFavoriteProperty_단지의_주택유형을_알_수_없으면_HousingTypeUndeterminedException을_던진다() {
        when(favoritePropertyRepository.existsByUser_UserIdAndComplex_ComplexId(1L, 10L)).thenReturn(false);
        when(complexRepository.findById(10L)).thenReturn(Optional.of(complex(10L, null)));

        assertThatThrownBy(() -> favoriteService.addFavoriteProperty(1L, new AddFavoritePropertyCommand(10L)))
                .isInstanceOf(HousingTypeUndeterminedException.class);
        verify(favoritePropertyRepository, never()).save(any());
    }

    @Test
    void addFavoriteProperty_성공하면_저장하고_응답을_반환한다() {
        Complex complex = complex(10L, "아파트");
        when(favoritePropertyRepository.existsByUser_UserIdAndComplex_ComplexId(1L, 10L)).thenReturn(false);
        when(complexRepository.findById(10L)).thenReturn(Optional.of(complex));
        when(userRepository.getReferenceById(1L)).thenReturn(User.builder().build());

        FavoritePropertyResponse response = favoriteService.addFavoriteProperty(1L, new AddFavoritePropertyCommand(10L));

        ArgumentCaptor<FavoriteProperty> captor = ArgumentCaptor.forClass(FavoriteProperty.class);
        verify(favoritePropertyRepository).save(captor.capture());
        assertThat(captor.getValue().getHousingType()).isEqualTo(HousingType.APT);
        assertThat(response.complexId()).isEqualTo(10L);
        assertThat(response.housingType()).isEqualTo(HousingType.APT);
    }

    @Test
    void addFavoriteProperty_연립다세대는_VILLA로_등록된다() {
        Complex complex = complex(10L, "연립다세대");
        when(favoritePropertyRepository.existsByUser_UserIdAndComplex_ComplexId(1L, 10L)).thenReturn(false);
        when(complexRepository.findById(10L)).thenReturn(Optional.of(complex));
        when(userRepository.getReferenceById(1L)).thenReturn(User.builder().build());

        FavoritePropertyResponse response = favoriteService.addFavoriteProperty(1L, new AddFavoritePropertyCommand(10L));

        assertThat(response.housingType()).isEqualTo(HousingType.VILLA);
    }

    /**
     * existsBy() 조회 이후 save() 이전에 동시에 들어온 다른 요청이 먼저 커밋을 끝낸 race condition을
     * DataIntegrityViolationException으로 재현한다 — AuthService.signup()과 같은 패턴(CLAUDE.md
     * "UNIQUE 제약 동시성 회귀 테스트 원칙"). save() 시점에 곧바로 예외가 나는 전제(GenerationType.IDENTITY)는
     * Mockito로 증명할 수 없어 FavoriteServiceMariaDbIT가 별도로 검증한다.
     */
    @Test
    void addFavoriteProperty_save시점에_UNIQUE_위반이_발생하면_DuplicateFavoriteException으로_변환한다() {
        Complex complex = complex(10L, "아파트");
        when(favoritePropertyRepository.existsByUser_UserIdAndComplex_ComplexId(1L, 10L)).thenReturn(false);
        when(complexRepository.findById(10L)).thenReturn(Optional.of(complex));
        when(userRepository.getReferenceById(1L)).thenReturn(User.builder().build());
        when(favoritePropertyRepository.save(any())).thenThrow(new DataIntegrityViolationException("dup"));

        assertThatThrownBy(() -> favoriteService.addFavoriteProperty(1L, new AddFavoritePropertyCommand(10L)))
                .isInstanceOf(DuplicateFavoriteException.class);
    }

    // ---- removeFavoriteProperty ----

    @Test
    void removeFavoriteProperty_존재하지_않으면_FavoriteNotFoundException을_던진다() {
        when(favoritePropertyRepository.findById(100L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> favoriteService.removeFavoriteProperty(1L, 100L))
                .isInstanceOf(FavoriteNotFoundException.class);
    }

    @Test
    void removeFavoriteProperty_소유자가_다르면_AccessDeniedException을_던지고_삭제하지_않는다() {
        User owner = mock(User.class);
        when(owner.getUserId()).thenReturn(2L);
        FavoriteProperty favorite = FavoriteProperty.register(owner, complex(10L, "아파트"), HousingType.APT);
        when(favoritePropertyRepository.findById(100L)).thenReturn(Optional.of(favorite));

        assertThatThrownBy(() -> favoriteService.removeFavoriteProperty(1L, 100L))
                .isInstanceOf(AccessDeniedException.class);
        verify(favoritePropertyRepository, never()).delete(any());
    }

    @Test
    void removeFavoriteProperty_소유자가_일치하면_삭제한다() {
        User owner = mock(User.class);
        when(owner.getUserId()).thenReturn(1L);
        FavoriteProperty favorite = FavoriteProperty.register(owner, complex(10L, "아파트"), HousingType.APT);
        when(favoritePropertyRepository.findById(100L)).thenReturn(Optional.of(favorite));

        favoriteService.removeFavoriteProperty(1L, 100L);

        verify(favoritePropertyRepository).delete(favorite);
    }

    // ---- getFavoriteProperties ----

    @Test
    void getFavoriteProperties_대표거래와_알림배지를_함께_채운다() {
        User owner = User.builder().build();
        Complex complex = complex(10L, "아파트");
        FavoriteProperty favorite = FavoriteProperty.register(owner, complex, HousingType.APT);
        when(favoritePropertyRepository.findByUser_UserId(1L)).thenReturn(List.of(favorite));

        Trade recentTrade = Trade.builder()
                .housingType(HousingType.APT).dealCategory(DealCategory.SALE)
                .datasetId("15126468").sggCd("11680").complex(complex)
                .excluUseArea(new BigDecimal("59.90")).dealDate(LocalDate.of(2026, 1, 10))
                .dealAmount(100_000L).cancelYn(false).dedupHash("hash-1").build();
        when(tradeRepository.findRecentTradesByComplexIds(List.of(10L)))
                .thenReturn(Map.of(10L, recentTrade));
        when(tradeRepository.findAverageSaleAmountGroupedByComplex(eq(List.of(10L)), any(), any()))
                .thenReturn(List.of());
        when(notificationSettingRepository.findFavoritePropertyIdsWithSetting(eq(1L), any()))
                .thenReturn(Collections.singletonList(null));

        List<FavoritePropertySummaryResponse> result = favoriteService.getFavoriteProperties(1L);

        assertThat(result).hasSize(1);
        assertThat(result.get(0).recentAmount()).isEqualTo(100_000L);
        assertThat(result.get(0).hasNotificationSetting()).isTrue();
    }

    @Test
    void getFavoriteProperties_대표거래가_없어도_목록에서_빠지지_않는다() {
        User owner = User.builder().build();
        Complex complex = complex(10L, "아파트");
        FavoriteProperty favorite = FavoriteProperty.register(owner, complex, HousingType.APT);
        when(favoritePropertyRepository.findByUser_UserId(1L)).thenReturn(List.of(favorite));
        when(tradeRepository.findRecentTradesByComplexIds(List.of(10L))).thenReturn(Map.of());
        when(tradeRepository.findAverageSaleAmountGroupedByComplex(eq(List.of(10L)), any(), any()))
                .thenReturn(List.of());

        List<FavoritePropertySummaryResponse> result = favoriteService.getFavoriteProperties(1L);

        assertThat(result).hasSize(1);
        assertThat(result.get(0).recentAmount()).isNull();
        assertThat(result.get(0).changeRate()).isNull();
    }

    @Test
    void getFavoriteProperties_이번달과_지난달_평균가가_있으면_변동률을_계산한다() {
        User owner = User.builder().build();
        Complex complex = complex(10L, "아파트");
        FavoriteProperty favorite = FavoriteProperty.register(owner, complex, HousingType.APT);
        when(favoritePropertyRepository.findByUser_UserId(1L)).thenReturn(List.of(favorite));
        when(tradeRepository.findRecentTradesByComplexIds(List.of(10L))).thenReturn(Map.of());

        LocalDate now = LocalDate.now(KST);
        when(tradeRepository.findAverageSaleAmountGroupedByComplex(eq(List.of(10L)), eq(now.minusMonths(1)), any()))
                .thenReturn(Collections.singletonList(new Object[] {10L, 110_000.0}));
        when(tradeRepository.findAverageSaleAmountGroupedByComplex(
                eq(List.of(10L)), eq(now.minusMonths(2)), eq(now.minusMonths(1))))
                .thenReturn(Collections.singletonList(new Object[] {10L, 100_000.0}));

        List<FavoritePropertySummaryResponse> result = favoriteService.getFavoriteProperties(1L);

        assertThat(result.get(0).changeRate()).isEqualByComparingTo(new BigDecimal("10.00"));
    }

    // ---- addFavoriteRegion ----

    @Test
    void addFavoriteRegion_이미_등록된_조합이면_DuplicateFavoriteRegionException을_던진다() {
        when(favoriteRegionRepository.existsByUser_UserIdAndLegalDistrictCode_LegalDongCd(1L, "1168010100"))
                .thenReturn(true);

        assertThatThrownBy(() -> favoriteService.addFavoriteRegion(1L, new AddFavoriteRegionCommand("1168010100")))
                .isInstanceOf(DuplicateFavoriteRegionException.class);
        verify(legalDistrictCodeRepository, never())
                .findByLegalDongCdAndIsActiveTrueAndEupmyeondongNameIsNotNull(any());
    }

    @Test
    void addFavoriteRegion_존재하지_않는_법정동코드면_RegionNotFoundException을_던진다() {
        when(favoriteRegionRepository.existsByUser_UserIdAndLegalDistrictCode_LegalDongCd(1L, "9999999999"))
                .thenReturn(false);
        when(legalDistrictCodeRepository.findByLegalDongCdAndIsActiveTrueAndEupmyeondongNameIsNotNull("9999999999"))
                .thenReturn(Optional.empty());

        assertThatThrownBy(() -> favoriteService.addFavoriteRegion(1L, new AddFavoriteRegionCommand("9999999999")))
                .isInstanceOf(RegionNotFoundException.class);
    }

    @Test
    void addFavoriteRegion_비활성이거나_대표행인_법정동코드면_RegionNotFoundException을_던진다() {
        when(favoriteRegionRepository.existsByUser_UserIdAndLegalDistrictCode_LegalDongCd(1L, "1168000000"))
                .thenReturn(false);
        // isActive=false이거나 eupmyeondongName=null(시도/시군구 대표행)인 코드는 이 파생 쿼리가 아예
        // 후보에서 제외한다 — findById()라면 통과시켰을 케이스다.
        when(legalDistrictCodeRepository.findByLegalDongCdAndIsActiveTrueAndEupmyeondongNameIsNotNull("1168000000"))
                .thenReturn(Optional.empty());

        assertThatThrownBy(() -> favoriteService.addFavoriteRegion(1L, new AddFavoriteRegionCommand("1168000000")))
                .isInstanceOf(RegionNotFoundException.class);
        verify(favoriteRegionRepository, never()).save(any());
    }

    @Test
    void addFavoriteRegion_성공하면_저장하고_응답을_반환한다() {
        when(favoriteRegionRepository.existsByUser_UserIdAndLegalDistrictCode_LegalDongCd(1L, "1168010100"))
                .thenReturn(false);
        when(legalDistrictCodeRepository.findByLegalDongCdAndIsActiveTrueAndEupmyeondongNameIsNotNull("1168010100"))
                .thenReturn(Optional.of(region("1168010100")));
        when(userRepository.getReferenceById(1L)).thenReturn(User.builder().build());

        FavoriteRegionResponse response = favoriteService.addFavoriteRegion(1L, new AddFavoriteRegionCommand("1168010100"));

        verify(favoriteRegionRepository).save(any(FavoriteRegion.class));
        assertThat(response.legalDongCd()).isEqualTo("1168010100");
        assertThat(response.fullPath()).isEqualTo("서울특별시 강남구 역삼동");
    }

    @Test
    void addFavoriteRegion_save시점에_UNIQUE_위반이_발생하면_DuplicateFavoriteRegionException으로_변환한다() {
        when(favoriteRegionRepository.existsByUser_UserIdAndLegalDistrictCode_LegalDongCd(1L, "1168010100"))
                .thenReturn(false);
        when(legalDistrictCodeRepository.findByLegalDongCdAndIsActiveTrueAndEupmyeondongNameIsNotNull("1168010100"))
                .thenReturn(Optional.of(region("1168010100")));
        when(userRepository.getReferenceById(1L)).thenReturn(User.builder().build());
        when(favoriteRegionRepository.save(any())).thenThrow(new DataIntegrityViolationException("dup"));

        assertThatThrownBy(() -> favoriteService.addFavoriteRegion(1L, new AddFavoriteRegionCommand("1168010100")))
                .isInstanceOf(DuplicateFavoriteRegionException.class);
    }

    // ---- removeFavoriteRegion ----

    @Test
    void removeFavoriteRegion_존재하지_않으면_FavoriteNotFoundException을_던진다() {
        when(favoriteRegionRepository.findById(100L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> favoriteService.removeFavoriteRegion(1L, 100L))
                .isInstanceOf(FavoriteNotFoundException.class);
    }

    @Test
    void removeFavoriteRegion_소유자가_다르면_AccessDeniedException을_던지고_삭제하지_않는다() {
        User owner = mock(User.class);
        when(owner.getUserId()).thenReturn(2L);
        FavoriteRegion favorite = FavoriteRegion.register(owner, region("1168010100"));
        when(favoriteRegionRepository.findById(100L)).thenReturn(Optional.of(favorite));

        assertThatThrownBy(() -> favoriteService.removeFavoriteRegion(1L, 100L))
                .isInstanceOf(AccessDeniedException.class);
        verify(favoriteRegionRepository, never()).delete(any());
    }

    @Test
    void removeFavoriteRegion_소유자가_일치하면_삭제한다() {
        User owner = mock(User.class);
        when(owner.getUserId()).thenReturn(1L);
        FavoriteRegion favorite = FavoriteRegion.register(owner, region("1168010100"));
        when(favoriteRegionRepository.findById(100L)).thenReturn(Optional.of(favorite));

        favoriteService.removeFavoriteRegion(1L, 100L);

        verify(favoriteRegionRepository).delete(favorite);
    }

    // ---- getFavoriteRegions ----

    @Test
    void getFavoriteRegions_RegionStatsCalculator_결과를_함께_반환한다() {
        User owner = mock(User.class);
        FavoriteRegion favorite = FavoriteRegion.register(owner, region("1168010100"));
        when(favoriteRegionRepository.findByUser_UserId(1L)).thenReturn(List.of(favorite));
        when(regionStatsCalculator.calculateBatch(List.of("1168010100"))).thenReturn(Map.of("1168010100",
                new RegionStats(new BigDecimal("110000"), new BigDecimal("10.00"), new BigDecimal("3000"), 4L)));

        List<FavoriteRegionSummaryResponse> result = favoriteService.getFavoriteRegions(1L);

        assertThat(result).hasSize(1);
        assertThat(result.get(0).legalDongCd()).isEqualTo("1168010100");
        assertThat(result.get(0).pricePerPyeong()).isEqualByComparingTo(new BigDecimal("3000"));
        assertThat(result.get(0).newTradeCount()).isEqualTo(4L);
    }
}
