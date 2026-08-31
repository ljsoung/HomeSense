package com.jiseong.homesense.batch.matcher;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.jiseong.homesense.batch.parser.dto.TradeDraft;
import com.jiseong.homesense.complex.entity.Complex;
import com.jiseong.homesense.complex.repository.ComplexRepository;
import com.jiseong.homesense.region.entity.LegalDistrictCode;
import com.jiseong.homesense.trade.entity.DealCategory;
import com.jiseong.homesense.trade.entity.HousingType;
import com.jiseong.homesense.trade.entity.MatchMethod;

@ExtendWith(MockitoExtension.class)
class ComplexMasterMatcherTest {

    private static final LegalDistrictCode YEOKSAM_DONG =
            legalDistrictCode("서울특별시", "강남구", "역삼동");

    @Mock
    private ComplexRepository complexRepository;

    @InjectMocks
    private ComplexMasterMatcher matcher;

    private static Complex complex(Long id, String name, String legalDongAddress) {
        return Complex.builder()
                .complexId(id)
                .complexName(name)
                .legalDongAddress(legalDongAddress)
                .build();
    }

    private static LegalDistrictCode legalDistrictCode(String sido, String sigungu, String dongRi) {
        return LegalDistrictCode.builder()
                .sidoName(sido)
                .sigunguName(sigungu)
                .eupmyeondongName(dongRi)
                .build();
    }

    /**
     * jibun·buildingName만 테스트별로 바꾸고 나머지는 BAT-MAT-02 매칭 로직과 무관한 값으로 채운다.
     */
    private static TradeDraft draft(String jibun, String buildingName) {
        return new TradeDraft(
                HousingType.APT,
                DealCategory.SALE,
                null,
                "15126468",
                "11680",
                "역삼동",
                buildingName,
                jibun,
                new BigDecimal("84.99"),
                (short) 10,
                (short) 2005,
                LocalDate.of(2024, 1, 15),
                120000L,
                null,
                null,
                null,
                "AGENT",
                "강남구",
                null,
                null,
                null,
                null,
                false,
                null);
    }

    @Test
    void 지번과_단지명이_모두_일치하면_EXACT_1점을_반환한다() {
        Complex candidate = complex(1L, "역삼래미안", "서울특별시 강남구 역삼동 123-4");
        when(complexRepository.findBySidoAndSigunguAndDongRi(any(), any(), any()))
                .thenReturn(List.of(candidate));

        MatchResult result = matcher.matchComplex(draft("123-4", "역삼래미안"), YEOKSAM_DONG);

        assertThat(result.complexId()).isEqualTo(1L);
        assertThat(result.matchMethod()).isEqualTo(MatchMethod.EXACT);
        assertThat(result.matchConfidence()).isEqualByComparingTo(new BigDecimal("1.000"));
    }

    @Test
    void 지번은_일치하지만_단지명이_다르면_EXACT를_유지하되_신뢰도를_낮춘다() {
        Complex candidate = complex(2L, "개명후아파트", "서울특별시 강남구 역삼동 123-4");
        when(complexRepository.findBySidoAndSigunguAndDongRi(any(), any(), any()))
                .thenReturn(List.of(candidate));

        MatchResult result = matcher.matchComplex(draft("123-4", "개명전아파트"), YEOKSAM_DONG);

        assertThat(result.complexId()).isEqualTo(2L);
        assertThat(result.matchMethod()).isEqualTo(MatchMethod.EXACT);
        assertThat(result.matchConfidence()).isEqualByComparingTo(new BigDecimal("0.800"));
    }

    @Test
    void 산번지와_일반지번은_다른_필지로_취급한다() {
        Complex candidate = complex(3L, "역삼래미안", "서울특별시 강남구 역삼동 산 123-4");
        when(complexRepository.findBySidoAndSigunguAndDongRi(any(), any(), any()))
                .thenReturn(List.of(candidate));

        MatchResult result = matcher.matchComplex(draft("123-4", "역삼래미안"), YEOKSAM_DONG);

        // 지번이 일치하지 않으므로 SIMILAR 경로로 넘어가고, 단지명이 완전히 같아 유사도 1.0 -> 최대 신뢰도로 채택된다.
        assertThat(result.complexId()).isEqualTo(3L);
        assertThat(result.matchMethod()).isEqualTo(MatchMethod.SIMILAR);
        assertThat(result.matchConfidence()).isEqualByComparingTo(new BigDecimal("0.850"));
    }

    @Test
    void legal_dong_address가_시도_시군구_동리를_포함한_전체_주소여도_지번을_추출해_EXACT로_매칭한다() {
        // 회귀 테스트: normalizeJibun이 문자열 맨 앞부터만 매치하던 시절에는 legal_dong_address가
        // "시도 시군구 동리 지번" 형태의 전체 주소라 절대 매치되지 않아 EXACT 경로가 항상 무너졌다.
        Complex candidate = complex(6L, "역삼래미안", "서울특별시 강남구 역삼동 산 45-6");
        when(complexRepository.findBySidoAndSigunguAndDongRi(any(), any(), any()))
                .thenReturn(List.of(candidate));

        MatchResult result = matcher.matchComplex(draft("산 45-6", "역삼래미안"), YEOKSAM_DONG);

        assertThat(result.complexId()).isEqualTo(6L);
        assertThat(result.matchMethod()).isEqualTo(MatchMethod.EXACT);
        assertThat(result.matchConfidence()).isEqualByComparingTo(new BigDecimal("1.000"));
    }

    @Test
    void 지번이_다르지만_단지명_유사도가_높으면_SIMILAR로_채택한다() {
        Complex candidate = complex(4L, "역삼래미안1차", "서울특별시 강남구 역삼동 999-9");
        when(complexRepository.findBySidoAndSigunguAndDongRi(any(), any(), any()))
                .thenReturn(List.of(candidate));

        MatchResult result = matcher.matchComplex(draft("123-4", "역삼래미안"), YEOKSAM_DONG);

        assertThat(result.complexId()).isEqualTo(4L);
        assertThat(result.matchMethod()).isEqualTo(MatchMethod.SIMILAR);
        assertThat(result.matchConfidence()).isGreaterThanOrEqualTo(new BigDecimal("0.600"));
        assertThat(result.matchConfidence()).isLessThan(new BigDecimal("0.850"));
    }

    @Test
    void 지번도_단지명도_불일치하면_매칭_실패로_처리한다() {
        Complex candidate = complex(5L, "전혀다른이름아파트", "서울특별시 강남구 역삼동 999-9");
        when(complexRepository.findBySidoAndSigunguAndDongRi(any(), any(), any()))
                .thenReturn(List.of(candidate));

        MatchResult result = matcher.matchComplex(draft("123-4", "역삼래미안"), YEOKSAM_DONG);

        assertThat(result.complexId()).isNull();
        assertThat(result.matchMethod()).isNull();
        assertThat(result.matchConfidence()).isNull();
    }

    @Test
    void 후보가_0건이면_예외없이_매칭_실패로_처리한다() {
        when(complexRepository.findBySidoAndSigunguAndDongRi(eq("서울특별시"), eq("강남구"), eq("역삼동")))
                .thenReturn(List.of());

        MatchResult result = matcher.matchComplex(draft("123-4", "역삼래미안"), YEOKSAM_DONG);

        assertThat(result.complexId()).isNull();
        assertThat(result.matchMethod()).isNull();
        assertThat(result.matchConfidence()).isNull();
    }

    @Test
    void 법정동_매핑에_실패해_legalDistrictCode가_없으면_매칭_실패로_처리한다() {
        MatchResult result = matcher.matchComplex(draft("123-4", "역삼래미안"), null);

        assertThat(result.complexId()).isNull();
        assertThat(result.matchMethod()).isNull();
        assertThat(result.matchConfidence()).isNull();
    }
}
