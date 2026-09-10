package com.jiseong.homesense.batch.matcher;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

import java.util.List;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.jiseong.homesense.region.entity.LegalDistrictCode;
import com.jiseong.homesense.region.repository.LegalDistrictCodeRepository;

@ExtendWith(MockitoExtension.class)
class LegalDistrictMatcherTest {

    private static final String SGG_CD = "11680";

    @Mock
    private LegalDistrictCodeRepository legalDistrictCodeRepository;

    @InjectMocks
    private LegalDistrictMatcher matcher;

    private static LegalDistrictCode districtOf(String legalDongCd, String eupmyeondongName) {
        return LegalDistrictCode.builder()
                .legalDongCd(legalDongCd)
                .legalDongName("서울특별시 강남구 " + eupmyeondongName)
                .sidoName("서울특별시")
                .sigunguName("강남구")
                .eupmyeondongName(eupmyeondongName)
                .isActive(true)
                .build();
    }

    @Test
    void 읍면동명이_일치하는_후보를_찾으면_그_엔티티를_반환한다() {
        LegalDistrictCode yeoksam = districtOf("1168010100", "역삼동");
        LegalDistrictCode samsung = districtOf("1168010500", "삼성동");
        when(legalDistrictCodeRepository.findByLegalDongCdStartingWithAndIsActiveTrue(SGG_CD))
                .thenReturn(List.of(yeoksam, samsung));

        var result = matcher.matchByTradeSggCd(SGG_CD, "역삼동");

        assertThat(result).contains(yeoksam);
    }

    @Test
    void 읍면동명과_일치하는_후보가_없으면_빈_결과를_반환한다() {
        when(legalDistrictCodeRepository.findByLegalDongCdStartingWithAndIsActiveTrue(SGG_CD))
                .thenReturn(List.of(districtOf("1168010100", "역삼동")));

        var result = matcher.matchByTradeSggCd(SGG_CD, "논현동");

        assertThat(result).isEmpty();
    }

    @Test
    void umdNm이_null이면_후보_조회_결과와_무관하게_빈_결과를_반환한다() {
        when(legalDistrictCodeRepository.findByLegalDongCdStartingWithAndIsActiveTrue(SGG_CD))
                .thenReturn(List.of(districtOf("1168010100", "역삼동")));

        var result = matcher.matchByTradeSggCd(SGG_CD, null);

        assertThat(result).isEmpty();
    }

    @Test
    void umdNm_앞뒤_공백은_무시하고_비교한다() {
        LegalDistrictCode yeoksam = districtOf("1168010100", "역삼동");
        when(legalDistrictCodeRepository.findByLegalDongCdStartingWithAndIsActiveTrue(SGG_CD))
                .thenReturn(List.of(yeoksam));

        var result = matcher.matchByTradeSggCd(SGG_CD, "  역삼동  ");

        assertThat(result).contains(yeoksam);
    }
}
