package com.jiseong.homesense.region.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Pageable;

import com.jiseong.homesense.favorite.entity.FavoriteRegion;
import com.jiseong.homesense.favorite.repository.FavoriteRegionRepository;
import com.jiseong.homesense.region.dto.InterestRegionSummaryResponse;
import com.jiseong.homesense.region.dto.RegionAutocompleteResponse;
import com.jiseong.homesense.region.dto.RegionStats;
import com.jiseong.homesense.region.entity.LegalDistrictCode;
import com.jiseong.homesense.region.repository.LegalDistrictCodeRepository;
import com.jiseong.homesense.user.entity.User;

@ExtendWith(MockitoExtension.class)
class RegionServiceTest {

    @Mock
    private LegalDistrictCodeRepository legalDistrictCodeRepository;
    @Mock
    private FavoriteRegionRepository favoriteRegionRepository;
    @Mock
    private RegionStatsCalculator regionStatsCalculator;

    private RegionService regionService;

    @BeforeEach
    void setUp() {
        regionService = new RegionService(legalDistrictCodeRepository, favoriteRegionRepository, regionStatsCalculator);
    }

    private static LegalDistrictCode legalDistrictCode(String legalDongCd, String sido, String sigungu, String dongRi) {
        return LegalDistrictCode.builder()
                .legalDongCd(legalDongCd)
                .legalDongName(sido + " " + sigungu + " " + dongRi)
                .sidoName(sido)
                .sigunguName(sigungu)
                .eupmyeondongName(dongRi)
                .isActive(true)
                .build();
    }

    @Test
    void query가_2자_미만이면_리포지토리를_조회하지_않고_빈_리스트를_반환한다() {
        List<RegionAutocompleteResponse> result = regionService.autocomplete("역");

        assertThat(result).isEmpty();
        verify(legalDistrictCodeRepository, never()).searchByNameContaining(any(), any());
    }

    @Test
    void query가_null이면_빈_리스트를_반환한다() {
        List<RegionAutocompleteResponse> result = regionService.autocomplete(null);

        assertThat(result).isEmpty();
    }

    @Test
    void query가_2자_이상이면_후보_목록을_전체_경로_문자열로_조립해_반환한다() {
        when(legalDistrictCodeRepository.searchByNameContaining(eq("역삼"), any(Pageable.class)))
                .thenReturn(List.of(legalDistrictCode("1168010100", "서울특별시", "강남구", "역삼동")));

        List<RegionAutocompleteResponse> result = regionService.autocomplete("역삼");

        assertThat(result).hasSize(1);
        assertThat(result.get(0).legalDongCd()).isEqualTo("1168010100");
        assertThat(result.get(0).fullPath()).isEqualTo("서울특별시 강남구 역삼동");
    }

    @Test
    void 관심지역이_없으면_빈_리스트를_반환한다() {
        when(favoriteRegionRepository.findByUser_UserId(1L)).thenReturn(List.of());

        List<InterestRegionSummaryResponse> result = regionService.getInterestSummary(1L);

        assertThat(result).isEmpty();
    }

    @Test
    void 관심지역마다_RegionStatsCalculator_결과를_함께_반환한다() {
        User user = User.createUser("user@test.com", "encoded", "닉네임");
        LegalDistrictCode code = legalDistrictCode("1168010100", "서울특별시", "강남구", "역삼동");
        FavoriteRegion favorite = FavoriteRegion.register(user, code);
        when(favoriteRegionRepository.findByUser_UserId(1L)).thenReturn(List.of(favorite));
        when(regionStatsCalculator.calculate("1168010100"))
                .thenReturn(new RegionStats(new BigDecimal("110000"), new BigDecimal("10.00"), new BigDecimal("3000"), 2L));

        List<InterestRegionSummaryResponse> result = regionService.getInterestSummary(1L);

        assertThat(result).hasSize(1);
        assertThat(result.get(0).legalDongCd()).isEqualTo("1168010100");
        assertThat(result.get(0).fullPath()).isEqualTo("서울특별시 강남구 역삼동");
        assertThat(result.get(0).avgPrice()).isEqualByComparingTo(new BigDecimal("110000"));
        assertThat(result.get(0).changeRate()).isEqualByComparingTo(new BigDecimal("10.00"));
    }
}
