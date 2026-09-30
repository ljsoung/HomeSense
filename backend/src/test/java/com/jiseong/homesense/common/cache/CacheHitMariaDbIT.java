package com.jiseong.homesense.common.cache;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.util.List;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.cache.CacheManager;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;
import org.testcontainers.containers.MariaDBContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import com.jiseong.homesense.complex.entity.Complex;
import com.jiseong.homesense.complex.repository.ComplexRepository;
import com.jiseong.homesense.region.entity.LegalDistrictCode;
import com.jiseong.homesense.region.repository.LegalDistrictCodeRepository;
import com.jiseong.homesense.search.entity.SearchLog;
import com.jiseong.homesense.search.repository.SearchLogRepository;
import com.jiseong.homesense.trade.entity.DealCategory;
import com.jiseong.homesense.trade.entity.HousingType;
import com.jiseong.homesense.trade.entity.Trade;
import com.jiseong.homesense.trade.repository.TradeRepository;

/**
 * COM-CACHE-01 캐시 히트 경로를 실제 Redis로 검증한다. 리스트를 캐싱하는 세 캐시가 첫 호출은 200인데 캐시
 * 히트부터 {@code SerializationException}으로 500을 냈다(2026-09-23 발견) — Mockito·WebMvc 슬라이스는 Redis
 * 읽기를 거치지 않아 잡지 못했다. 같은 요청을 두 번 보내 두 번째가 캐시에서 읽혀도 같은 응답인지 본다.
 *
 * <p>Redis는 Testcontainers가 아니라 로컬 Redis(localhost:6379)를 쓴다 — 다른 IT와 같은 알려진 격리 한계다
 * (CLAUDE.md 백로그). 그래서 이 네 캐시를 테스트 앞뒤로 비운다.
 */
@SpringBootTest
@AutoConfigureMockMvc
@Testcontainers
@Transactional
@Tag("integration")
class CacheHitMariaDbIT {

    @Container
    static final MariaDBContainer<?> MARIADB = new MariaDBContainer<>("mariadb:10.11")
            .withDatabaseName("homesense_it")
            .withUsername("homesense")
            .withPassword("homesense");

    @DynamicPropertySource
    static void datasourceProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", MARIADB::getJdbcUrl);
        registry.add("spring.datasource.username", MARIADB::getUsername);
        registry.add("spring.datasource.password", MARIADB::getPassword);
        registry.add("spring.datasource.driver-class-name", MARIADB::getDriverClassName);
        registry.add("spring.jpa.hibernate.ddl-auto", () -> "none");
        registry.add("spring.sql.init.mode", () -> "always");
        registry.add("spring.sql.init.schema-locations", () -> "classpath:testcontainers/complex-search-schema.sql");
    }

    private static final List<String> CACHES = List.of(CacheNames.COMPLEX_DETAIL, CacheNames.POPULAR_COMPLEXES,
            CacheNames.REGION_AUTOCOMPLETE, CacheNames.POPULAR_KEYWORDS);

    @Autowired
    private MockMvc mockMvc;
    @Autowired
    private CacheManager cacheManager;
    @Autowired
    private StringRedisTemplate redisTemplate;
    @Autowired
    private LegalDistrictCodeRepository legalDistrictCodeRepository;
    @Autowired
    private ComplexRepository complexRepository;
    @Autowired
    private TradeRepository tradeRepository;
    @Autowired
    private SearchLogRepository searchLogRepository;

    private Complex complex;

    @BeforeEach
    void setUp() {
        clearCaches();
        LegalDistrictCode code = legalDistrictCodeRepository.saveAndFlush(LegalDistrictCode.builder()
                .legalDongCd("4111112900").legalDongName("경기도 수원시 장안구 파장동").sidoName("경기도")
                .sigunguName("수원시 장안구").eupmyeondongName("파장동").isActive(true)
                .dataVersion(LocalDate.of(2026, 9, 17)).build());
        complex = complexRepository.saveAndFlush(Complex.builder()
                .sourceComplexCd("SRC-CACHE").complexName("북수원자이").complexType("아파트")
                .sido("경기도").sigungu("수원장안구").dongRi("파장동")
                .legalDistrictCode(code).legalDongAddress("경기도 수원장안구 파장동 632 북수원자이")
                .elevatorPassengerCount((short) 1).elevatorCargoCount((short) 0).elevatorCombinedCount((short) 0)
                .dataUpdatedAt(LocalDate.of(2026, 1, 1)).build());
        tradeRepository.saveAndFlush(Trade.builder()
                .housingType(HousingType.APT).dealCategory(DealCategory.SALE)
                .datasetId("15126468").sggCd("41111").complex(complex)
                .excluUseArea(new BigDecimal("84.00")).dealDate(LocalDate.now().minusDays(3))
                .dealAmount(90000L).cancelYn(false).dedupHash("cache-hit-it").build());
        searchLogRepository.saveAndFlush(SearchLog.record("수원"));
    }

    @AfterEach
    void tearDown() {
        clearCaches();
    }

    @Test
    void 인기_검색어는_캐시_히트에도_같은_응답이다() throws Exception {
        String first = getTwiceAndCompare("/api/search/popular?limit=5");

        assertThat(first).contains("수원");
        assertThat(redisTemplate.hasKey(CacheNames.POPULAR_KEYWORDS + "::5")).isTrue();
    }

    @Test
    void 인기_단지는_캐시_히트에도_같은_응답이다() throws Exception {
        String first = getTwiceAndCompare("/api/complexes/popular?limit=8");

        assertThat(first).contains("북수원자이");
        assertThat(redisTemplate.hasKey(CacheNames.POPULAR_COMPLEXES + "::8")).isTrue();
    }

    @Test
    void 지역_자동완성은_캐시_히트에도_같은_응답이다() throws Exception {
        String first = getTwiceAndCompare("/api/regions?query=파장");

        assertThat(first).contains("4111112900");
        assertThat(redisTemplate.hasKey(CacheNames.REGION_AUTOCOMPLETE + "::파장")).isTrue();
    }

    @Test
    void 단지_상세는_캐시_히트에도_같은_응답이다() throws Exception {
        String first = getTwiceAndCompare("/api/complexes/" + complex.getComplexId());

        assertThat(first).contains("북수원자이");
        assertThat(redisTemplate.hasKey(CacheNames.COMPLEX_DETAIL + "::" + complex.getComplexId())).isTrue();
    }

    @Test
    void 배포_전_직렬화기가_저장한_엔트리도_읽힌다() throws Exception {
        // 배포 전 GenericJacksonJsonRedisSerializer(unsafe default typing)가 Stream.toList()를 저장한 형태.
        // 옛 서버는 이걸 읽지 못해 500을 냈지만, 타입이 명시된 새 직렬화기는 @class를 모르는 속성으로 무시하고 읽는다.
        String key = CacheNames.POPULAR_KEYWORDS + "::5";
        redisTemplate.opsForValue().set(key,
                "[{\"@class\":\"com.jiseong.homesense.search.dto.PopularKeywordResponse\",\"keyword\":\"old\"}]");

        mockMvc.perform(get("/api/search/popular?limit=5"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data[0].keyword").value("old"));
    }

    @Test
    void 읽을_수_없는_엔트리는_500이_아니라_미스로_처리되고_새_값으로_덮어써진다() throws Exception {
        String key = CacheNames.POPULAR_KEYWORDS + "::5";
        redisTemplate.opsForValue().set(key, "not-json");

        mockMvc.perform(get("/api/search/popular?limit=5"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data[0].keyword").value("수원"));

        assertThat(redisTemplate.opsForValue().get(key)).contains("수원");
        mockMvc.perform(get("/api/search/popular?limit=5"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data[0].keyword").value("수원"));
    }

    private String getTwiceAndCompare(String url) throws Exception {
        String first = dataOf(url);
        String second = dataOf(url);
        assertThat(second).isEqualTo(first);
        return first;
    }

    private String dataOf(String url) throws Exception {
        String body = mockMvc.perform(get(url))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString(StandardCharsets.UTF_8);
        return body.replaceAll("\"timestamp\":\"[^\"]*\"", "");
    }

    private void clearCaches() {
        CACHES.forEach(name -> cacheManager.getCache(name).invalidate());
    }
}
