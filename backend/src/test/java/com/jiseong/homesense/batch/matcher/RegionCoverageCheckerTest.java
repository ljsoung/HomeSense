package com.jiseong.homesense.batch.matcher;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

import java.util.Arrays;
import java.util.List;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.jiseong.homesense.complex.repository.ComplexRepository;
import com.jiseong.homesense.region.repository.LegalDistrictCodeRepository;

/**
 * 2026년 화성시 일반구 신설·인천 행정체제 개편·전남광주통합특별시 출범 당시 legal_district_code
 * 참조자료가 노후화되며 몇 달간 감지되지 못했던 lawd_cd 커버리지 공백(CLAUDE.md 참고)을 재발 방지
 * 차원에서 조기에 로그로 드러내는 경량 체크 — 정교한 판정이 아니라 "커버되지 않는 조합이 존재한다"는
 * 신호 하나가 목적이라 개수 계산의 정확성만 검증한다.
 */
@ExtendWith(MockitoExtension.class)
class RegionCoverageCheckerTest {

    @Mock
    private ComplexRepository complexRepository;
    @Mock
    private LegalDistrictCodeRepository legalDistrictCodeRepository;

    private RegionCoverageChecker checker() {
        return new RegionCoverageChecker(complexRepository, legalDistrictCodeRepository);
    }

    /**
     * {@code List.of(new Object[]{...})}는 단일 원소가 아니라 varargs로 스프레드돼
     * {@code List<Object>}로 추론된다 — 이 헬퍼가 {@code List<Object[]>}로 타입을 고정한다.
     */
    private static List<Object[]> pairs(Object[]... rows) {
        return Arrays.asList(rows);
    }

    @Test
    void complex의_시도_시군구_조합이_legal_district_code에_그대로_존재하면_공백이_없다() {
        when(complexRepository.findDistinctSidoSigunguPairs())
                .thenReturn(pairs(new Object[]{"서울특별시", "강남구"}));
        when(legalDistrictCodeRepository.findDistinctActiveSidoSigunguPairs())
                .thenReturn(pairs(new Object[]{"서울특별시", "강남구"}));

        int uncoveredCount = checker().checkCoverage();

        assertThat(uncoveredCount).isZero();
    }

    @Test
    void legal_district_code에_공백이_있는_표기를_정규화해서_비교하면_커버된_것으로_판정한다() {
        // complex.sigungu는 xlsx 원본대로 붙여쓰기("화성만세구")지만 legal_district_code.sigunguName은
        // CSV 원본대로 공백 있는 "화성시 만세구" — SigunguNormalizer가 이 둘을 같은 조합으로 본다.
        when(complexRepository.findDistinctSidoSigunguPairs())
                .thenReturn(pairs(new Object[]{"경기도", "화성만세구"}));
        when(legalDistrictCodeRepository.findDistinctActiveSidoSigunguPairs())
                .thenReturn(pairs(new Object[]{"경기도", "화성시 만세구"}));

        int uncoveredCount = checker().checkCoverage();

        assertThat(uncoveredCount).isZero();
    }

    @Test
    void legal_district_code에_없는_시도_시군구_조합은_공백으로_카운트한다() {
        // 2026년 실제 사례 재현: legal_district_code가 노후화되어 신설/개편된 지역이 아예 없는 경우.
        when(complexRepository.findDistinctSidoSigunguPairs())
                .thenReturn(pairs(
                        new Object[]{"전남광주통합특별시", "북구"},
                        new Object[]{"서울특별시", "강남구"}));
        when(legalDistrictCodeRepository.findDistinctActiveSidoSigunguPairs())
                .thenReturn(pairs(new Object[]{"서울특별시", "강남구"}));

        int uncoveredCount = checker().checkCoverage();

        assertThat(uncoveredCount).isEqualTo(1);
    }

    @Test
    void sigungu가_NULL인_세종특별자치시도_null_안전하게_비교한다() {
        when(complexRepository.findDistinctSidoSigunguPairs())
                .thenReturn(pairs(new Object[]{"세종특별자치시", null}));
        when(legalDistrictCodeRepository.findDistinctActiveSidoSigunguPairs())
                .thenReturn(pairs(new Object[]{"세종특별자치시", null}));

        int uncoveredCount = checker().checkCoverage();

        assertThat(uncoveredCount).isZero();
    }
}
