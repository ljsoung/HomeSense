package com.jiseong.homesense.batch.matcher;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
import java.util.List;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.jiseong.homesense.batch.parser.dto.TradeDraft;
import com.jiseong.homesense.complex.entity.Complex;
import com.jiseong.homesense.complex.repository.ComplexRepository;
import com.jiseong.homesense.trade.entity.HousingType;
import com.jiseong.homesense.trade.entity.MatchMethod;

@ExtendWith(MockitoExtension.class)
class ComplexMasterMatcherTest {

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

    private static TradeDraft draft(String jibun, String buildingName) {
        return new TradeDraft(HousingType.APT, "서울특별시", "강남구", "역삼동", jibun, buildingName);
    }

    @Test
    void 지번과_단지명이_모두_일치하면_EXACT_1점을_반환한다() {
        Complex candidate = complex(1L, "역삼래미안", "123-4");
        when(complexRepository.findBySidoAndSigunguAndDongRi(any(), any(), any()))
                .thenReturn(List.of(candidate));

        MatchResult result = matcher.matchComplex(draft("123-4", "역삼래미안"));

        assertThat(result.complexId()).isEqualTo(1L);
        assertThat(result.matchMethod()).isEqualTo(MatchMethod.EXACT);
        assertThat(result.matchConfidence()).isEqualByComparingTo(new BigDecimal("1.000"));
    }

    @Test
    void 지번은_일치하지만_단지명이_다르면_EXACT를_유지하되_신뢰도를_낮춘다() {
        Complex candidate = complex(2L, "개명후아파트", "123-4");
        when(complexRepository.findBySidoAndSigunguAndDongRi(any(), any(), any()))
                .thenReturn(List.of(candidate));

        MatchResult result = matcher.matchComplex(draft("123-4", "개명전아파트"));

        assertThat(result.complexId()).isEqualTo(2L);
        assertThat(result.matchMethod()).isEqualTo(MatchMethod.EXACT);
        assertThat(result.matchConfidence()).isEqualByComparingTo(new BigDecimal("0.800"));
    }

    @Test
    void 산번지와_일반지번은_다른_필지로_취급한다() {
        Complex candidate = complex(3L, "역삼래미안", "산 123-4");
        when(complexRepository.findBySidoAndSigunguAndDongRi(any(), any(), any()))
                .thenReturn(List.of(candidate));

        MatchResult result = matcher.matchComplex(draft("123-4", "역삼래미안"));

        // 지번이 일치하지 않으므로 SIMILAR 경로로 넘어가고, 단지명이 완전히 같아 유사도 1.0 -> 최대 신뢰도로 채택된다.
        assertThat(result.complexId()).isEqualTo(3L);
        assertThat(result.matchMethod()).isEqualTo(MatchMethod.SIMILAR);
        assertThat(result.matchConfidence()).isEqualByComparingTo(new BigDecimal("0.850"));
    }

    @Test
    void 지번이_다르지만_단지명_유사도가_높으면_SIMILAR로_채택한다() {
        Complex candidate = complex(4L, "역삼래미안1차", "999-9");
        when(complexRepository.findBySidoAndSigunguAndDongRi(any(), any(), any()))
                .thenReturn(List.of(candidate));

        MatchResult result = matcher.matchComplex(draft("123-4", "역삼래미안"));

        assertThat(result.complexId()).isEqualTo(4L);
        assertThat(result.matchMethod()).isEqualTo(MatchMethod.SIMILAR);
        assertThat(result.matchConfidence()).isGreaterThanOrEqualTo(new BigDecimal("0.600"));
        assertThat(result.matchConfidence()).isLessThan(new BigDecimal("0.850"));
    }

    @Test
    void 지번도_단지명도_불일치하면_매칭_실패로_처리한다() {
        Complex candidate = complex(5L, "전혀다른이름아파트", "999-9");
        when(complexRepository.findBySidoAndSigunguAndDongRi(any(), any(), any()))
                .thenReturn(List.of(candidate));

        MatchResult result = matcher.matchComplex(draft("123-4", "역삼래미안"));

        assertThat(result.complexId()).isNull();
        assertThat(result.matchMethod()).isNull();
        assertThat(result.matchConfidence()).isNull();
    }

    @Test
    void 후보가_0건이면_예외없이_매칭_실패로_처리한다() {
        when(complexRepository.findBySidoAndSigunguAndDongRi(eq("서울특별시"), eq("강남구"), eq("역삼동")))
                .thenReturn(List.of());

        MatchResult result = matcher.matchComplex(draft("123-4", "역삼래미안"));

        assertThat(result.complexId()).isNull();
        assertThat(result.matchMethod()).isNull();
        assertThat(result.matchConfidence()).isNull();
    }
}
