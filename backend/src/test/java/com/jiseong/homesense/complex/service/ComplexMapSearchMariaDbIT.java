package com.jiseong.homesense.complex.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.cache.Cache;
import org.springframework.cache.CacheManager;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.data.domain.PageRequest;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;
import org.testcontainers.containers.MariaDBContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import com.jiseong.homesense.common.cache.CacheNames;
import com.jiseong.homesense.complex.dto.BoundsCondition;
import com.jiseong.homesense.complex.dto.ComplexMapPointResponse;
import com.jiseong.homesense.complex.dto.ComplexMapSearchResponse;
import com.jiseong.homesense.complex.dto.ComplexSearchCondition;
import com.jiseong.homesense.complex.dto.ComplexSummaryResponse;
import com.jiseong.homesense.complex.dto.SortCondition;
import com.jiseong.homesense.complex.entity.Complex;
import com.jiseong.homesense.complex.repository.ComplexRepository;
import com.jiseong.homesense.trade.entity.DealCategory;
import com.jiseong.homesense.trade.entity.HousingType;
import com.jiseong.homesense.trade.entity.MatchMethod;
import com.jiseong.homesense.trade.entity.RentType;
import com.jiseong.homesense.trade.entity.Trade;
import com.jiseong.homesense.trade.repository.TradeRepository;

/**
 * API-CPX-01 GET /api/complexes/map을 실제 MariaDB로 검증한다 — 좌표 BETWEEN 경계, 좌표 NULL 제외, SRCH-01과 같은
 * 필터·대표거래 규칙, 상한 초과 시 truncated는 Mockito로 증명할 수 없다.
 *
 * <p>상한을 2로 낮춰(properties) truncated 경계를 작은 시드로 만든다. 클래스 레벨 {@code @Transactional}이라 시드는
 * 테스트마다 롤백된다(CLAUDE.md "MariaDB IT 테스트에 @Test 메서드가 둘 이상이면" 절). MockMvc는 실제 보안 필터
 * 체인을 태워 비로그인 허용도 함께 확인한다.
 */
@SpringBootTest(properties = "homesense.complex.map.max-results=" + ComplexMapSearchMariaDbIT.LIMIT)
@AutoConfigureMockMvc
@Testcontainers
@Transactional
@Tag("integration")
class ComplexMapSearchMariaDbIT {

    static final int LIMIT = 2;

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

    /** 시드 단지가 모두 들어가는 범위(서울 일부). 경계 테스트는 따로 정한다. */
    private static final BoundsCondition SEOUL =
            new BoundsCondition(new BigDecimal("37.4"), new BigDecimal("126.9"), new BigDecimal("37.6"), new BigDecimal("127.1"));

    @Autowired
    private ComplexService complexService;
    @Autowired
    private ComplexRepository complexRepository;
    @Autowired
    private TradeRepository tradeRepository;
    @Autowired
    private MockMvc mockMvc;
    @Autowired
    private CacheManager cacheManager;

    private int hashSeq;

    @BeforeEach
    void resetSeq() {
        hashSeq = 0;
    }

    private Complex complex(String src, String lat, String lng, String precision) {
        return complexRepository.saveAndFlush(Complex.builder()
                .sourceComplexCd("SRC-" + src).complexName("단지" + src).complexType("아파트")
                .sido("서울특별시").sigungu("강남구").dongRi("역삼동")
                .latitude(lat == null ? null : new BigDecimal(lat))
                .longitude(lng == null ? null : new BigDecimal(lng))
                .locationPrecision(precision)
                .elevatorPassengerCount((short) 1).elevatorCargoCount((short) 0).elevatorCombinedCount((short) 0)
                .dataUpdatedAt(LocalDate.of(2026, 1, 1)).build());
    }

    private Trade sale(Complex c, LocalDate date, long amount, MatchMethod matchMethod, Short floor, boolean cancelled) {
        return tradeRepository.saveAndFlush(Trade.builder()
                .housingType(HousingType.APT).dealCategory(DealCategory.SALE).datasetId("15126468").sggCd("11680")
                .complex(c).excluUseArea(new BigDecimal("84.90")).dealDate(date).dealAmount(amount)
                .cancelYn(cancelled).matchMethod(matchMethod).floor(floor)
                .dedupHash("map-" + (++hashSeq)).build());
    }

    private Trade rent(Complex c, LocalDate date, RentType rentType, long deposit, long monthly, MatchMethod matchMethod) {
        return tradeRepository.saveAndFlush(Trade.builder()
                .housingType(HousingType.APT).dealCategory(DealCategory.RENT).rentType(rentType).datasetId("15126474")
                .sggCd("11680").complex(c).excluUseArea(new BigDecimal("59.90")).dealDate(date)
                .depositAmount(deposit).monthlyRentAmount(monthly).cancelYn(false).matchMethod(matchMethod)
                .dedupHash("map-" + (++hashSeq)).build());
    }

    private static List<Long> ids(ComplexMapSearchResponse response) {
        return response.items().stream().map(ComplexMapPointResponse::complexId).toList();
    }

    @Test
    void 범위의_양_끝은_포함하고_조금이라도_벗어나면_뺀다() {
        Complex sw = complex("SW", "37.4000000", "126.9000000", "PRECISE");
        Complex ne = complex("NE", "37.6000000", "127.1000000", "PRECISE");
        Complex southOut = complex("S", "37.3999999", "127.0000000", "PRECISE");
        Complex eastOut = complex("E", "37.5000000", "127.1000001", "PRECISE");
        for (Complex c : List.of(sw, ne, southOut, eastOut)) {
            sale(c, LocalDate.of(2026, 9, 1), 50000L, MatchMethod.EXACT, (short) 3, false);
        }

        ComplexMapSearchResponse response = complexService.searchInBounds(SEOUL, ComplexSearchCondition.builder().build());

        assertThat(ids(response)).containsExactlyInAnyOrder(sw.getComplexId(), ne.getComplexId());
        assertThat(response.truncated()).isFalse();
    }

    @Test
    void 좌표가_NULL인_단지는_빠진다() {
        Complex located = complex("OK", "37.5", "127.0", "PRECISE");
        Complex noLat = complex("NLAT", null, "127.0", null);
        Complex noLng = complex("NLNG", "37.5", null, null);
        Complex none = complex("NONE", null, null, null);
        for (Complex c : List.of(located, noLat, noLng, none)) {
            sale(c, LocalDate.of(2026, 9, 1), 50000L, MatchMethod.EXACT, (short) 3, false);
        }

        ComplexMapSearchResponse response = complexService.searchInBounds(
                new BoundsCondition(new BigDecimal("-90"), new BigDecimal("-180"), new BigDecimal("90"), new BigDecimal("180")),
                ComplexSearchCondition.builder().build());

        assertThat(ids(response)).containsExactly(located.getComplexId());
    }

    @Test
    void 필터에_맞는_거래가_없는_단지는_빠지고_대표거래는_필터를_만족하는_최신_거래다() {
        Complex saleOnly = complex("SALE", "37.50", "127.00", "PRECISE");
        sale(saleOnly, LocalDate.of(2026, 9, 1), 90000L, MatchMethod.EXACT, (short) 10, false);
        Complex mixed = complex("MIX", "37.51", "127.01", "APPROXIMATE");
        // 매매가 더 최신이지만 월세 필터에서는 월세 거래가 대표다.
        rent(mixed, LocalDate.of(2026, 8, 1), RentType.WOLSE, 5000L, 120L, MatchMethod.SIMILAR);
        sale(mixed, LocalDate.of(2026, 9, 10), 80000L, MatchMethod.EXACT, (short) 5, false);
        Complex cancelledOnly = complex("CXL", "37.52", "127.02", "PRECISE");
        sale(cancelledOnly, LocalDate.of(2026, 9, 1), 70000L, MatchMethod.EXACT, (short) 5, true);
        Complex noTrade = complex("NOTRADE", "37.53", "127.03", "PRECISE");

        ComplexMapSearchResponse wolse = complexService.searchInBounds(SEOUL,
                ComplexSearchCondition.builder().rentType(RentType.WOLSE).build());
        assertThat(ids(wolse)).containsExactly(mixed.getComplexId());
        ComplexMapPointResponse point = wolse.items().get(0);
        assertThat(point.representativeDealCategory()).isEqualTo(DealCategory.RENT);
        assertThat(point.rentType()).isEqualTo(RentType.WOLSE);
        assertThat(point.representativeAmount()).isEqualTo(5000L);
        assertThat(point.monthlyRentAmount()).isEqualTo(120L);
        assertThat(point.matchMethod()).isEqualTo(MatchMethod.SIMILAR);
        assertThat(point.locationPrecision()).isEqualTo("APPROXIMATE");
        assertThat(point.dongRi()).isEqualTo("역삼동");

        ComplexMapSearchResponse saleUnder85k = complexService.searchInBounds(SEOUL,
                ComplexSearchCondition.builder().dealCategory(DealCategory.SALE).amountMax(85000L).build());
        assertThat(ids(saleUnder85k)).containsExactly(mixed.getComplexId());
        assertThat(saleUnder85k.items().get(0).representativeAmount()).isEqualTo(80000L);
        assertThat(saleUnder85k.items().get(0).matchMethod()).isEqualTo(MatchMethod.EXACT);
        assertThat(saleUnder85k.items().get(0).floor()).isEqualTo((short) 5);

        ComplexMapSearchResponse noFilter = complexService.searchInBounds(SEOUL, ComplexSearchCondition.builder().build());
        assertThat(ids(noFilter)).containsExactlyInAnyOrder(saleOnly.getComplexId(), mixed.getComplexId())
                .doesNotContain(cancelledOnly.getComplexId(), noTrade.getComplexId());
        // 거래가 여러 건인 단지(mixed)도 대표거래 하나로 한 번만 나온다 — 한 번 더 나오면 상한(2)을 넘어 truncated가 된다.
        assertThat(noFilter.truncated()).isFalse();
    }

    @Test
    void 같은_필터면_마커의_대표거래가_SRCH01_검색_카드와_같다() {
        Complex a = complex("A", "37.50", "127.00", "PRECISE");
        // 같은 날짜 두 건 — 큰 trade_id가 대표다.
        sale(a, LocalDate.of(2026, 9, 1), 60000L, MatchMethod.SIMILAR, (short) 2, false);
        sale(a, LocalDate.of(2026, 9, 1), 61000L, MatchMethod.EXACT, (short) 9, false);
        Complex b = complex("B", "37.51", "127.01", "PRECISE");
        rent(b, LocalDate.of(2026, 9, 5), RentType.JEONSE, 30000L, 0L, MatchMethod.SIMILAR);
        sale(b, LocalDate.of(2026, 8, 5), 70000L, MatchMethod.EXACT, (short) 4, false);

        for (ComplexSearchCondition condition : List.of(
                ComplexSearchCondition.builder().build(),
                ComplexSearchCondition.builder().dealCategory(DealCategory.SALE).build(),
                ComplexSearchCondition.builder().rentType(RentType.JEONSE).build())) {
            Map<Long, ComplexSummaryResponse> cards = complexRepository
                    .search(condition.toBuilder().sort(SortCondition.LATEST).build(), null, PageRequest.of(0, 50))
                    .getContent().stream()
                    .collect(Collectors.toMap(ComplexSummaryResponse::complexId, Function.identity()));
            ComplexMapSearchResponse markers = complexService.searchInBounds(SEOUL, condition);

            assertThat(markers.truncated()).as("단지당 한 행").isFalse();
            assertThat(markers.items()).hasSameSizeAs(cards.values());
            for (ComplexMapPointResponse marker : markers.items()) {
                ComplexSummaryResponse card = cards.get(marker.complexId());
                assertThat(card).as("조건 %s의 단지 %d", condition, marker.complexId()).isNotNull();
                assertThat(marker.matchMethod()).isEqualTo(card.matchMethod());
                assertThat(marker.representativeAmount()).isEqualTo(card.representativeAmount());
                assertThat(marker.representativeDealDate()).isEqualTo(card.representativeDealDate());
                assertThat(marker.floor()).isEqualTo(card.floor());
                assertThat(marker.rentType()).isEqualTo(card.rentType());
            }
        }
        // 필터 없음: A는 같은 날 두 건 중 큰 trade_id(EXACT·9층), B는 더 최신인 전세.
        Map<Long, ComplexMapPointResponse> noFilter = complexService
                .searchInBounds(SEOUL, ComplexSearchCondition.builder().build()).items().stream()
                .collect(Collectors.toMap(ComplexMapPointResponse::complexId, Function.identity()));
        assertThat(noFilter.get(a.getComplexId()).matchMethod()).isEqualTo(MatchMethod.EXACT);
        assertThat(noFilter.get(a.getComplexId()).floor()).isEqualTo((short) 9);
        assertThat(noFilter.get(b.getComplexId()).rentType()).isEqualTo(RentType.JEONSE);
    }

    /** 리포지토리의 지도 정렬 키와 같은 식. 시드 id로 기대 순서를 계산한다. */
    private static long sampleKey(long complexId) {
        return complexId * 2_654_435_761L % 2_147_483_647L;
    }

    private List<Long> seedFive() {
        List<Long> seeded = new ArrayList<>();
        for (String src : List.of("1", "2", "3", "4", "5")) {
            Complex c = complex(src, "37.5" + src, "127.0" + src, "PRECISE");
            sale(c, LocalDate.of(2026, 9, 1), 50000L, MatchMethod.EXACT, (short) 3, false);
            seeded.add(c.getComplexId());
        }
        return seeded;
    }

    @Test
    void 상한을_넘으면_해시_순으로_상한까지만_주고_truncated가_true다() {
        List<Long> seeded = seedFive();
        List<Long> expected = seeded.stream()
                .sorted(Comparator.comparingLong(ComplexMapSearchMariaDbIT::sampleKey).thenComparing(Long::longValue))
                .limit(LIMIT).toList();
        // complex_id 순 앞 2건과 다르게 골라지는 시드여야 이 테스트가 정렬을 실제로 검증한다.
        assertThat(expected).isNotEqualTo(seeded.subList(0, LIMIT));

        ComplexMapSearchResponse response = complexService.searchInBounds(SEOUL, ComplexSearchCondition.builder().build());

        assertThat(response.limit()).isEqualTo(LIMIT);
        assertThat(response.truncated()).isTrue();
        assertThat(ids(response)).containsExactlyElementsOf(expected);
    }

    @Test
    void 잘릴_때도_같은_범위와_필터면_두_번_호출한_결과가_같다() {
        seedFive();
        ComplexSearchCondition condition = ComplexSearchCondition.builder().dealCategory(DealCategory.SALE).build();

        ComplexMapSearchResponse first = complexService.searchInBounds(SEOUL, condition);
        ComplexMapSearchResponse second = complexService.searchInBounds(SEOUL, condition);

        assertThat(first.truncated()).isTrue();
        assertThat(second.items()).isEqualTo(first.items());
    }

    @Test
    void 정확히_상한만큼이면_truncated가_false다() {
        for (String src : List.of("1", "2")) {
            sale(complex(src, "37.50", "127.00", "PRECISE"), LocalDate.of(2026, 9, 1), 50000L, MatchMethod.EXACT,
                    (short) 3, false);
        }

        ComplexMapSearchResponse response = complexService.searchInBounds(SEOUL, ComplexSearchCondition.builder().build());

        assertThat(response.items()).hasSize(LIMIT);
        assertThat(response.truncated()).isFalse();
    }

    @Test
    void 정확_매칭이어도_좌표가_근사면_검색_지도_상세_세_API가_모두_근사_배지를_준다() throws Exception {
        Complex approx = complex("PBAPX", "37.50", "127.00", "APPROXIMATE");
        sale(approx, LocalDate.of(2026, 9, 1), 50000L, MatchMethod.EXACT, (short) 3, false);
        Complex precise = complex("PBPRE", "37.51", "127.01", "PRECISE");
        sale(precise, LocalDate.of(2026, 9, 1), 50000L, MatchMethod.EXACT, (short) 3, false);
        // 상세는 Redis 캐시(complexDetailV4)를 거친다 — 개발 Redis에 같은 id의 옛 항목이 있으면 그 값이 나오므로 지운다.
        Cache detailCache = cacheManager.getCache(CacheNames.COMPLEX_DETAIL);
        detailCache.evictIfPresent(approx.getComplexId());
        detailCache.evictIfPresent(precise.getComplexId());
        try {
            String approxPath = "$.data[?(@.complexId == " + approx.getComplexId() + ")]";
            String precisePath = "$.data[?(@.complexId == " + precise.getComplexId() + ")]";
            mockMvc.perform(get("/api/complexes/search").param("keyword", "단지PB"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath(approxPath + ".matchMethod").value("EXACT"))
                    .andExpect(jsonPath(approxPath + ".precisionBadge").value("APPROXIMATE"))
                    .andExpect(jsonPath(precisePath + ".precisionBadge").value("PRECISE"));

            mockMvc.perform(get("/api/complexes/map").param("bounds", "37.4,126.9,37.6,127.1"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.data.items[?(@.complexId == " + approx.getComplexId() + ")].precisionBadge")
                            .value("APPROXIMATE"))
                    .andExpect(jsonPath("$.data.items[?(@.complexId == " + precise.getComplexId() + ")].precisionBadge")
                            .value("PRECISE"));

            mockMvc.perform(get("/api/complexes/" + approx.getComplexId()))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.data.matchMethod").value("EXACT"))
                    .andExpect(jsonPath("$.data.locationPrecision").value("APPROXIMATE"))
                    .andExpect(jsonPath("$.data.precisionBadge").value("APPROXIMATE"));
            mockMvc.perform(get("/api/complexes/" + precise.getComplexId()))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.data.precisionBadge").value("PRECISE"));
        } finally {
            detailCache.evictIfPresent(approx.getComplexId());
            detailCache.evictIfPresent(precise.getComplexId());
        }
    }

    @Test
    void 비로그인으로_HTTP_요청하면_필터를_적용한_응답을_준다() throws Exception {
        Complex c = complex("HTTP", "37.50", "127.00", "PRECISE");
        rent(c, LocalDate.of(2026, 9, 1), RentType.WOLSE, 5000L, 120L, MatchMethod.SIMILAR);
        Complex saleOnly = complex("HTTPSALE", "37.51", "127.01", "PRECISE");
        sale(saleOnly, LocalDate.of(2026, 9, 1), 50000L, MatchMethod.EXACT, (short) 3, false);

        mockMvc.perform(get("/api/complexes/map")
                        .param("bounds", "37.4,126.9,37.6,127.1")
                        .param("rentType", "WOLSE")
                        .param("amountMax", "6000"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.limit").value(LIMIT))
                .andExpect(jsonPath("$.data.truncated").value(false))
                .andExpect(jsonPath("$.data.items.length()").value(1))
                .andExpect(jsonPath("$.data.items[0].complexId").value(c.getComplexId()))
                .andExpect(jsonPath("$.data.items[0].latitude").value(37.5))
                .andExpect(jsonPath("$.data.items[0].matchMethod").value("SIMILAR"))
                .andExpect(jsonPath("$.data.items[0].monthlyRentAmount").value(120));

        mockMvc.perform(get("/api/complexes/map").param("bounds", "37.6,126.9,37.4,127.1"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.error.code").value("INVALID_BOUNDS"));
    }
}
