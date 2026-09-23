package com.jiseong.homesense.region.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.jiseong.homesense.batch.matcher.LegalDistrictCodeReloadedEvent;
import com.jiseong.homesense.region.entity.LegalDistrictCode;
import com.jiseong.homesense.region.repository.LegalDistrictCodeRepository;

@ExtendWith(MockitoExtension.class)
class RegionCodePrefixResolverTest {

    @Mock
    private LegalDistrictCodeRepository legalDistrictCodeRepository;

    private static LegalDistrictCode code(String cd, String sido, String sigungu, String emd, boolean active) {
        return LegalDistrictCode.builder().legalDongCd(cd).legalDongName(sido).sidoName(sido).sigunguName(sigungu)
                .eupmyeondongName(emd).isActive(active).dataVersion(LocalDate.of(2026, 9, 17)).build();
    }

    /** 2026-09-23 활성 코드 전수 조사에서 실제로 확인한 코드들. */
    private static final List<LegalDistrictCode> CODES = List.of(
            code("4100000000", "경기도", null, null, true),
            code("4111000000", "경기도", "수원시", null, true),
            code("4111100000", "경기도", "수원시 장안구", null, true),
            code("4111112900", "경기도", "수원시 장안구", "파장동", true),
            code("4111300000", "경기도", "수원시 권선구", null, true),
            code("4155000000", "경기도", "안성시", null, true),
            code("4155025000", "경기도", "안성시", "공도읍", true),
            code("4155025021", "경기도", "안성시", "공도읍 만정리", true),
            code("3611000000", "세종특별자치시", null, null, true),
            code("3611010100", "세종특별자치시", null, "반곡동", true),
            code("4374000000", "충청북도", "영동군", null, true),
            code("4374500000", "충청북도", "증평군", null, true),
            code("2811000000", "인천광역시", "중구", null, false));

    private final Map<String, String> prefixes = RegionCodePrefixResolver.computePrefixes(CODES);

    @Test
    void 시도는_앞_2자리다() {
        assertThat(prefixes.get("4100000000")).isEqualTo("41");
    }

    @Test
    void 구를_가진_시는_구_코드까지_포함하도록_앞_4자리다() {
        assertThat(prefixes.get("4111000000")).isEqualTo("4111");
        assertThat("4111112900").startsWith(prefixes.get("4111000000"));
        assertThat("4111300000").startsWith(prefixes.get("4111000000"));
    }

    @Test
    void 구와_구가_없는_시는_앞_5자리다() {
        assertThat(prefixes.get("4111100000")).isEqualTo("41111");
        assertThat(prefixes.get("4155000000")).isEqualTo("41550");
    }

    @Test
    void 세종은_시군구_계층이_없어도_하위_동을_포함한다() {
        assertThat(prefixes.get("3611000000")).isEqualTo("36110");
        assertThat("3611010100").startsWith(prefixes.get("3611000000"));
    }

    @Test
    void 읍면동은_앞_8자리_리는_10자리_전체다() {
        assertThat(prefixes.get("4155025000")).isEqualTo("41550250");
        assertThat("4155025021").startsWith(prefixes.get("4155025000"));
        assertThat(prefixes.get("4155025021")).isEqualTo("4155025021");
        assertThat(prefixes.get("4111112900")).isEqualTo("41111129");
    }

    /** 영동군(43740)과 증평군(43745)은 무관한데 4자리를 공유한다 — 숫자 규칙이면 영동군 검색에 증평군이 섞인다. */
    @Test
    void 구를_가진_시_판정은_코드가_아니라_이름으로_한다() {
        assertThat(prefixes.get("4374000000")).isEqualTo("43740");
        assertThat("4374500000").doesNotStartWith(prefixes.get("4374000000"));
    }

    @Test
    void 폐지된_코드는_맵에_없다() {
        assertThat(prefixes).doesNotContainKey("2811000000");
    }

    @Test
    void 판정용_데이터는_한_번만_로드하고_재적재_이벤트_후에만_다시_로드한다() {
        when(legalDistrictCodeRepository.findAll()).thenReturn(CODES);
        RegionCodePrefixResolver resolver = new RegionCodePrefixResolver(legalDistrictCodeRepository);

        assertThat(resolver.prefixOf("4111000000")).contains("4111");
        assertThat(resolver.prefixOf("9999999999")).isEmpty();
        verify(legalDistrictCodeRepository, times(1)).findAll();

        resolver.onLegalDistrictCodeReloaded(new LegalDistrictCodeReloadedEvent());
        resolver.prefixOf("4111000000");
        verify(legalDistrictCodeRepository, times(2)).findAll();
    }
}
