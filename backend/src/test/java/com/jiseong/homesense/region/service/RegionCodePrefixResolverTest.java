package com.jiseong.homesense.region.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.data.redis.RedisConnectionFailureException;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;

import com.jiseong.homesense.batch.matcher.LegalDistrictCodeReloadedEvent;
import com.jiseong.homesense.region.entity.LegalDistrictCode;
import com.jiseong.homesense.region.repository.LegalDistrictCodeRepository;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class RegionCodePrefixResolverTest {

    @Mock
    private LegalDistrictCodeRepository legalDistrictCodeRepository;
    @Mock
    private StringRedisTemplate redisTemplate;
    @Mock
    private ValueOperations<String, String> valueOperations;

    @BeforeEach
    void stubRedis() {
        when(redisTemplate.opsForValue()).thenReturn(valueOperations);
    }

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
    void 버전이_그대로면_판정용_데이터를_한_번만_로드한다() {
        when(legalDistrictCodeRepository.findAll()).thenReturn(CODES);
        when(valueOperations.get(RegionCodePrefixResolver.VERSION_KEY)).thenReturn("v1");
        RegionCodePrefixResolver resolver = resolver();

        assertThat(resolver.prefixOf("4111000000")).contains("4111");
        assertThat(resolver.prefixOf("9999999999")).isEmpty();
        verify(legalDistrictCodeRepository, times(1)).findAll();
    }

    @Test
    void 다른_프로세스가_재적재해_Redis_버전이_바뀌면_다시_로드한다() {
        when(legalDistrictCodeRepository.findAll()).thenReturn(CODES);
        when(valueOperations.get(RegionCodePrefixResolver.VERSION_KEY)).thenReturn(null, "v2", "v2");
        RegionCodePrefixResolver resolver = resolver();

        resolver.prefixOf("4111000000");
        resolver.prefixOf("4111000000"); // 이 프로세스에는 이벤트가 오지 않았지만 버전이 바뀌었다
        resolver.prefixOf("4111000000");
        verify(legalDistrictCodeRepository, times(2)).findAll();
    }

    @Test
    void 재적재_이벤트를_받으면_로컬_맵을_비우고_Redis_버전을_바꾼다() {
        when(legalDistrictCodeRepository.findAll()).thenReturn(CODES);
        when(valueOperations.get(RegionCodePrefixResolver.VERSION_KEY)).thenReturn("v1");
        RegionCodePrefixResolver resolver = resolver();
        resolver.prefixOf("4111000000");

        resolver.onLegalDistrictCodeReloaded(new LegalDistrictCodeReloadedEvent());

        verify(valueOperations).set(eq(RegionCodePrefixResolver.VERSION_KEY), anyString());
        resolver.prefixOf("4111000000");
        verify(legalDistrictCodeRepository, times(2)).findAll();
    }

    @Test
    void Redis를_읽지_못하면_보유한_맵을_그대로_쓴다() {
        when(legalDistrictCodeRepository.findAll()).thenReturn(CODES);
        when(valueOperations.get(RegionCodePrefixResolver.VERSION_KEY))
                .thenReturn("v1")
                .thenThrow(new RedisConnectionFailureException("down"));
        RegionCodePrefixResolver resolver = resolver();

        resolver.prefixOf("4111000000");
        assertThat(resolver.prefixOf("4111000000")).contains("4111");
        verify(legalDistrictCodeRepository, times(1)).findAll();
    }

    @Test
    void 맵이_없는데_Redis를_읽지_못해도_맵을_만들어_응답한다() {
        when(legalDistrictCodeRepository.findAll()).thenReturn(CODES);
        when(valueOperations.get(RegionCodePrefixResolver.VERSION_KEY))
                .thenThrow(new RedisConnectionFailureException("down"));

        assertThat(resolver().prefixOf("4111000000")).contains("4111");
    }

    @Test
    void Redis_버전_쓰기가_실패해도_예외를_전파하지_않고_로컬_맵은_비운다() {
        when(legalDistrictCodeRepository.findAll()).thenReturn(CODES);
        when(valueOperations.get(RegionCodePrefixResolver.VERSION_KEY)).thenReturn("v1");
        doThrow(new RedisConnectionFailureException("down"))
                .when(valueOperations).set(eq(RegionCodePrefixResolver.VERSION_KEY), anyString());
        RegionCodePrefixResolver resolver = resolver();
        resolver.prefixOf("4111000000");

        resolver.onLegalDistrictCodeReloaded(new LegalDistrictCodeReloadedEvent());

        resolver.prefixOf("4111000000");
        verify(legalDistrictCodeRepository, times(2)).findAll();
    }

    private RegionCodePrefixResolver resolver() {
        return new RegionCodePrefixResolver(legalDistrictCodeRepository, redisTemplate);
    }
}
