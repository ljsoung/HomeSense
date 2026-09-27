package com.jiseong.homesense.common.cache;

import static org.assertj.core.api.Assertions.assertThat;

import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.math.BigDecimal;
import java.nio.ByteBuffer;
import java.time.Duration;
import java.time.LocalDate;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Stream;

import org.junit.jupiter.api.Test;
import org.springframework.data.redis.cache.RedisCacheConfiguration;

import com.jiseong.homesense.complex.dto.ComplexDetailResponse;
import com.jiseong.homesense.complex.dto.ComplexSummaryResponse;
import com.jiseong.homesense.complex.entity.Complex;
import com.jiseong.homesense.region.dto.RegionAutocompleteResponse;
import com.jiseong.homesense.region.entity.LegalDistrictCode;
import com.jiseong.homesense.search.dto.PopularKeywordResponse;
import com.jiseong.homesense.trade.entity.DealCategory;
import com.jiseong.homesense.trade.entity.HousingType;
import com.jiseong.homesense.trade.entity.MatchMethod;
import com.jiseong.homesense.trade.entity.RentType;
import com.jiseong.homesense.trade.entity.Trade;

class CacheConfigTest {

    private final CacheConfig cacheConfig = new CacheConfig();
    private final Map<String, RedisCacheConfiguration> configs = cacheConfig.cacheConfigurations();

    @Test
    void 기본_TTL은_24시간이다() {
        assertThat(ttl(cacheConfig.redisCacheConfiguration())).isEqualTo(Duration.ofHours(24));
        assertThat(ttl(configs.get(CacheNames.COMPLEX_DETAIL))).isEqualTo(Duration.ofHours(24));
    }

    @Test
    void 인기_검색어만_TTL이_1시간이다() {
        assertThat(ttl(configs.get(CacheNames.POPULAR_KEYWORDS))).isEqualTo(Duration.ofHours(1));
    }

    @Test
    void null_캐싱을_막지_않는다_disableCachingNullValues는_저장_시도_자체를_예외로_거부하기_때문이다() {
        // 코드리뷰(PR #14)에서 지적된 대로 disableCachingNullValues()는 "null은 조용히 캐싱을
        // 건너뛴다"가 아니라 "null을 캐시에 넣으려는 시도를 IllegalArgumentException으로 거부한다"라
        // unless 조건 없는 @Cacheable(null 반환 가능)이 깨질 수 있어 의도적으로 켜지 않는다.
        configs.values().forEach(config -> assertThat(config.getAllowCacheNullValues()).isTrue());
    }

    @Test
    void 등록된_캐시는_정확히_CacheNames_상수들이다() throws IllegalAccessException {
        // 미등록 캐시는 자동 생성하지 않으므로(disableCreateOnMissingCache) 등록 누락은 런타임 실패가 된다.
        // 반대로 CacheNames에 없는 이름을 등록하면 상수를 거치지 않은 이름이 생긴다. 빈에 선언된 이름이 전부
        // 등록돼 있는지는 CacheNameRegistrationTest가 본다 — 두 테스트를 합쳐 "캐시 이름은 CacheNames 상수로만"을 강제한다.
        Set<String> constants = new HashSet<>();
        for (Field field : CacheNames.class.getDeclaredFields()) {
            if (Modifier.isStatic(field.getModifiers()) && field.getType() == String.class) {
                constants.add((String) field.get(null));
            }
        }
        assertThat(configs.keySet()).containsExactlyInAnyOrderElementsOf(constants);
    }

    // 회귀: Stream.toList()(final ImmutableCollections.ListN)가 캐시 히트 시 SerializationException을 냈다.

    @Test
    void 인기_검색어_리스트가_왕복된다() {
        List<PopularKeywordResponse> value = Stream.of("강남", "수원").map(PopularKeywordResponse::new).toList();

        assertThat(roundTrip(CacheNames.POPULAR_KEYWORDS, value)).isEqualTo(value);
    }

    @Test
    void 지역_자동완성_리스트가_왕복된다() {
        LegalDistrictCode code = LegalDistrictCode.builder().legalDongCd("4111112900")
                .legalDongName("경기도 수원시 장안구 파장동").sidoName("경기도").sigunguName("수원시 장안구")
                .eupmyeondongName("파장동").isActive(true).dataVersion(LocalDate.of(2026, 9, 17)).build();
        List<RegionAutocompleteResponse> value = Stream.of(code).map(RegionAutocompleteResponse::from).toList();

        assertThat(roundTrip(CacheNames.REGION_AUTOCOMPLETE, value)).isEqualTo(value);
    }

    @Test
    void 빈_리스트도_왕복된다() {
        assertThat(roundTrip(CacheNames.REGION_AUTOCOMPLETE, List.of())).isEqualTo(List.of());
    }

    @Test
    void 인기_단지_리스트가_enum과_날짜와_금액을_포함해_왕복된다() {
        Complex complex = complex();
        Trade trade = Trade.builder().housingType(HousingType.APT).dealCategory(DealCategory.RENT)
                .rentType(RentType.WOLSE).complex(complex).excluUseArea(new BigDecimal("84.97"))
                .dealDate(LocalDate.of(2026, 9, 1)).depositAmount(5000L).monthlyRentAmount(120L)
                .floor((short) 12).matchMethod(MatchMethod.EXACT).cancelYn(false).build();
        List<ComplexSummaryResponse> value = Stream.of(trade)
                .map(t -> ComplexSummaryResponse.of(complex, t)).toList();

        List<?> read = (List<?>) roundTrip(CacheNames.POPULAR_COMPLEXES, value);

        assertThat(read).isEqualTo(value);
        assertThat(read.get(0)).isInstanceOf(ComplexSummaryResponse.class);
    }

    @Test
    void 단지_상세가_왕복된다() {
        ComplexDetailResponse value = ComplexDetailResponse.from(complex());

        assertThat(roundTrip(CacheNames.COMPLEX_DETAIL, value)).isEqualTo(value);
    }

    private static Complex complex() {
        return Complex.builder().sourceComplexCd("SRC-1").complexName("북수원자이").complexType("아파트")
                .sido("경기도").sigungu("수원장안구").dongRi("파장동").legalDongAddress("경기도 수원장안구 파장동 632")
                .approvalDate(LocalDate.of(2010, 5, 1)).householdCount(500)
                .elevatorPassengerCount((short) 1).elevatorCargoCount((short) 0).elevatorCombinedCount((short) 0)
                .dataUpdatedAt(LocalDate.of(2026, 1, 1)).build();
    }

    private Object roundTrip(String cacheName, Object value) {
        RedisCacheConfiguration config = configs.get(cacheName);
        ByteBuffer written = config.getValueSerializationPair().write(value);
        byte[] bytes = new byte[written.remaining()];
        written.get(bytes);
        return config.getValueSerializationPair().read(ByteBuffer.wrap(Arrays.copyOf(bytes, bytes.length)));
    }

    private static Duration ttl(RedisCacheConfiguration config) {
        return config.getTtlFunction().getTimeToLive("any-key", "any-value");
    }
}
