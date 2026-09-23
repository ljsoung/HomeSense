package com.jiseong.homesense.complex.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.MediaType;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;
import org.testcontainers.containers.MariaDBContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import com.jiseong.homesense.batch.matcher.LegalDistrictCodeReloadedEvent;
import com.jiseong.homesense.complex.dto.ComplexSearchCondition;
import com.jiseong.homesense.complex.dto.ComplexSummaryResponse;
import com.jiseong.homesense.complex.dto.SortCondition;
import com.jiseong.homesense.complex.entity.Complex;
import com.jiseong.homesense.complex.repository.ComplexRepository;
import com.jiseong.homesense.region.entity.LegalDistrictCode;
import com.jiseong.homesense.region.repository.LegalDistrictCodeRepository;
import com.jiseong.homesense.region.service.RegionCodePrefixResolver;
import com.jiseong.homesense.search.repository.SearchLogRepository;
import com.jiseong.homesense.trade.entity.DealCategory;
import com.jiseong.homesense.trade.entity.HousingType;
import com.jiseong.homesense.trade.entity.RentType;
import com.jiseong.homesense.trade.entity.Trade;
import com.jiseong.homesense.trade.repository.TradeRepository;

/**
 * API-CPX-01 regionCode 계층 검색·keyword 필터·전세/월세 구분과 API-SEARCH-01 기록 분리를 실제 MariaDB로
 * 검증한다. prefix LIKE, LIKE 이스케이프, 대표거래 서브쿼리와의 결합은 Mockito로 증명할 수 없다.
 *
 * <p>RegionCodePrefixResolver는 법정동코드 맵을 싱글턴으로 캐시하므로, 각 테스트가 시드한 코드로 다시
 * 만들도록 {@code @BeforeEach}에서 재적재 이벤트를 흉내 내 비운다. 클래스 레벨 {@code @Transactional}이라
 * 시드는 테스트마다 롤백된다(CLAUDE.md "MariaDB IT 테스트에 @Test 메서드가 둘 이상이면" 절).
 */
@SpringBootTest
@AutoConfigureMockMvc
@Testcontainers
@Transactional
@Tag("integration")
class ComplexSearchRegionKeywordMariaDbIT {

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

    @Autowired
    private ComplexService complexService;
    @Autowired
    private ComplexRepository complexRepository;
    @Autowired
    private TradeRepository tradeRepository;
    @Autowired
    private LegalDistrictCodeRepository legalDistrictCodeRepository;
    @Autowired
    private RegionCodePrefixResolver regionCodePrefixResolver;
    @Autowired
    private SearchLogRepository searchLogRepository;
    @Autowired
    private MockMvc mockMvc;

    private Complex paJang;      // 수원시 장안구 파장동
    private Complex gwonSeon;    // 수원시 권선구 세류동
    private Complex anseongDong; // 안성시 봉산동
    private Complex anseongRi;   // 안성시 공도읍 만정리
    private Complex yeongdong;   // 영동군 영동읍 (증평군과 4자리 공유)
    private Complex jeungpyeong; // 증평군 증평읍

    @BeforeEach
    void setUp() {
        regionCodePrefixResolver.onLegalDistrictCodeReloaded(new LegalDistrictCodeReloadedEvent());

        LegalDistrictCode gyeonggi = code("4100000000", "경기도", null, null);
        code("4111000000", "경기도", "수원시", null);
        code("4111100000", "경기도", "수원시 장안구", null);
        LegalDistrictCode paJangCd = code("4111112900", "경기도", "수원시 장안구", "파장동");
        code("4111300000", "경기도", "수원시 권선구", null);
        LegalDistrictCode seRyuCd = code("4111312900", "경기도", "수원시 권선구", "세류동");
        code("4155000000", "경기도", "안성시", null);
        LegalDistrictCode bongSanCd = code("4155010100", "경기도", "안성시", "봉산동");
        code("4155025000", "경기도", "안성시", "공도읍");
        LegalDistrictCode manJeongCd = code("4155025021", "경기도", "안성시", "공도읍 만정리");
        code("4374000000", "충청북도", "영동군", null);
        LegalDistrictCode yeongdongCd = code("4374025000", "충청북도", "영동군", "영동읍");
        code("4374500000", "충청북도", "증평군", null);
        LegalDistrictCode jeungpyeongCd = code("4374525000", "충청북도", "증평군", "증평읍");
        assertThat(gyeonggi).isNotNull();

        paJang = complex("PJ", "북수원자이", paJangCd, "경기도 수원장안구 파장동 632 북수원자이");
        gwonSeon = complex("GS", "세류푸르지오", seRyuCd, "경기도 수원권선구 세류동 1 세류푸르지오");
        anseongDong = complex("AD", "한주아파트", bongSanCd, "경기도 안성시 봉산동 42 한주아파트");
        anseongRi = complex("AR", "공도우방", manJeongCd, "경기도 안성시 공도읍 만정리 10 공도우방");
        yeongdong = complex("YD", "영동자이", yeongdongCd, "충청북도 영동군 영동읍 1 영동자이");
        jeungpyeong = complex("JP", "증평자이", jeungpyeongCd, "충청북도 증평군 증평읍 1 증평자이");

        for (Complex c : List.of(paJang, gwonSeon, anseongDong, anseongRi, yeongdong, jeungpyeong)) {
            sale(c, LocalDate.of(2026, 8, 1), 50000L);
        }
    }

    private LegalDistrictCode code(String cd, String sido, String sigungu, String emd) {
        String name = String.join(" ", java.util.stream.Stream.of(sido, sigungu, emd).filter(p -> p != null).toList());
        return legalDistrictCodeRepository.saveAndFlush(LegalDistrictCode.builder().legalDongCd(cd).legalDongName(name)
                .sidoName(sido).sigunguName(sigungu).eupmyeondongName(emd).isActive(true)
                .dataVersion(LocalDate.of(2026, 9, 17)).build());
    }

    private Complex complex(String src, String name, LegalDistrictCode code, String address) {
        return complexRepository.saveAndFlush(Complex.builder()
                .sourceComplexCd("SRC-" + src).complexName(name).complexType("아파트")
                .legalDistrictCode(code).legalDongAddress(address)
                .elevatorPassengerCount((short) 1).elevatorCargoCount((short) 0).elevatorCombinedCount((short) 0)
                .dataUpdatedAt(LocalDate.of(2026, 1, 1)).build());
    }

    private void sale(Complex c, LocalDate date, long amount) {
        tradeRepository.saveAndFlush(trade(c, DealCategory.SALE, null, date, amount, null));
    }

    private Trade trade(Complex c, DealCategory category, RentType rentType, LocalDate date, Long dealAmount,
            Long deposit) {
        return Trade.builder()
                .housingType(HousingType.APT).dealCategory(category).rentType(rentType)
                .datasetId("15126468").sggCd("41111").complex(c)
                .excluUseArea(new BigDecimal("84.00")).dealDate(date)
                .dealAmount(dealAmount).depositAmount(deposit).cancelYn(false)
                .dedupHash("h-" + c.getComplexId() + "-" + date + "-" + category + "-" + rentType + "-" + dealAmount
                        + "-" + deposit)
                .build();
    }

    private List<Long> search(ComplexSearchCondition condition) {
        return complexService.search(condition, PageRequest.of(0, 50)).getContent().stream()
                .map(ComplexSummaryResponse::complexId).toList();
    }

    private List<Long> byRegion(String regionCode) {
        return search(ComplexSearchCondition.builder().regionCode(regionCode).sort(SortCondition.LATEST).build());
    }

    private List<Long> byKeyword(String keyword) {
        return search(ComplexSearchCondition.builder().keyword(keyword).sort(SortCondition.LATEST).build());
    }

    @Test
    void 시도_코드는_그_시도의_단지_전부다() {
        assertThat(byRegion("4100000000")).containsExactlyInAnyOrder(
                paJang.getComplexId(), gwonSeon.getComplexId(), anseongDong.getComplexId(), anseongRi.getComplexId());
    }

    @Test
    void 구를_가진_시_코드는_산하_구의_단지를_모두_포함한다() {
        assertThat(byRegion("4111000000"))
                .containsExactlyInAnyOrder(paJang.getComplexId(), gwonSeon.getComplexId());
        assertThat(byRegion("4111100000")).containsExactly(paJang.getComplexId());
    }

    @Test
    void 구가_없는_시_코드는_읍면동과_리의_단지를_모두_포함한다() {
        assertThat(byRegion("4155000000"))
                .containsExactlyInAnyOrder(anseongDong.getComplexId(), anseongRi.getComplexId());
    }

    @Test
    void 읍면동_코드와_리_코드는_그_아래_단지만이다() {
        assertThat(byRegion("4155025000")).containsExactly(anseongRi.getComplexId());
        assertThat(byRegion("4155025021")).containsExactly(anseongRi.getComplexId());
        assertThat(byRegion("4111112900")).containsExactly(paJang.getComplexId());
    }

    @Test
    void 영동군_검색에_4자리를_공유하는_증평군이_섞이지_않는다() {
        assertThat(byRegion("4374000000")).containsExactly(yeongdong.getComplexId());
        assertThat(byRegion("4374500000")).containsExactly(jeungpyeong.getComplexId());
    }

    @Test
    void 존재하지_않는_코드는_빈_결과다() {
        assertThat(byRegion("9999999999")).isEmpty();
    }

    @Test
    void keyword는_단지명에_부분_일치한다() {
        assertThat(byKeyword("푸르지")).containsExactly(gwonSeon.getComplexId());
    }

    @Test
    void keyword는_주소_텍스트에도_부분_일치한다() {
        assertThat(byKeyword("봉산동")).containsExactly(anseongDong.getComplexId());
        assertThat(byKeyword("안성시"))
                .containsExactlyInAnyOrder(anseongDong.getComplexId(), anseongRi.getComplexId());
        // complex 주소는 "수원장안구"라 "수원시"는 legal_district_code.legal_dong_name으로만 걸린다.
        assertThat(byKeyword("수원시"))
                .containsExactlyInAnyOrder(paJang.getComplexId(), gwonSeon.getComplexId());
    }

    @Test
    void keyword의_LIKE_와일드카드는_문자_그대로_취급한다() {
        assertThat(byKeyword("%%")).isEmpty();
        assertThat(byKeyword("__")).isEmpty();
        assertThat(byKeyword("!%")).isEmpty();

        Complex percentNamed = complex("PCT", "100%행복", null, "경기도 안성시 봉산동 50 100%행복");
        sale(percentNamed, LocalDate.of(2026, 8, 1), 50000L);
        assertThat(byKeyword("0%행")).containsExactly(percentNamed.getComplexId());
    }

    @Test
    void regionCode_keyword_거래유형_필터가_모두_AND로_결합된다() {
        tradeRepository.saveAndFlush(trade(anseongDong, DealCategory.RENT, RentType.JEONSE,
                LocalDate.of(2026, 8, 20), null, 30000L));
        tradeRepository.saveAndFlush(trade(anseongRi, DealCategory.RENT, RentType.JEONSE,
                LocalDate.of(2026, 8, 20), null, 90000L));

        List<Long> result = search(ComplexSearchCondition.builder()
                .regionCode("4155000000").keyword("안성").rentType(RentType.JEONSE)
                .amountMin(20000L).amountMax(40000L).sort(SortCondition.LATEST).build());

        assertThat(result).containsExactly(anseongDong.getComplexId());
    }

    @Test
    void 매매_전세_월세는_서로의_거래로_섞이지_않고_대표거래도_그_유형의_최신_거래다() {
        tradeRepository.saveAndFlush(trade(paJang, DealCategory.RENT, RentType.JEONSE,
                LocalDate.of(2026, 8, 10), null, 40000L));
        tradeRepository.saveAndFlush(trade(paJang, DealCategory.RENT, RentType.WOLSE,
                LocalDate.of(2026, 8, 25), null, 5000L));

        ComplexSearchCondition base = ComplexSearchCondition.builder().regionCode("4111112900")
                .sort(SortCondition.LATEST).build();
        ComplexSummaryResponse sale = complexService.search(base.toBuilder().dealCategory(DealCategory.SALE).build(),
                PageRequest.of(0, 10)).getContent().get(0);
        ComplexSummaryResponse jeonse = complexService.search(base.toBuilder().rentType(RentType.JEONSE).build(),
                PageRequest.of(0, 10)).getContent().get(0);
        ComplexSummaryResponse wolse = complexService.search(base.toBuilder().rentType(RentType.WOLSE).build(),
                PageRequest.of(0, 10)).getContent().get(0);

        assertThat(sale.representativeDealCategory()).isEqualTo(DealCategory.SALE);
        assertThat(sale.representativeAmount()).isEqualTo(50000L);
        assertThat(jeonse.representativeDealDate()).isEqualTo(LocalDate.of(2026, 8, 10));
        assertThat(jeonse.representativeAmount()).isEqualTo(40000L);
        assertThat(wolse.representativeDealDate()).isEqualTo(LocalDate.of(2026, 8, 25));
        assertThat(wolse.representativeAmount()).isEqualTo(5000L);
        // 전월세 전체(dealCategory=RENT)면 가장 최근인 월세가 대표거래다.
        ComplexSummaryResponse rent = complexService.search(base.toBuilder().dealCategory(DealCategory.RENT).build(),
                PageRequest.of(0, 10)).getContent().get(0);
        assertThat(rent.representativeDealDate()).isEqualTo(LocalDate.of(2026, 8, 25));
    }

    @Test
    void 전세_보증금_범위_필터는_depositAmount_기준이다() {
        tradeRepository.saveAndFlush(trade(anseongDong, DealCategory.RENT, RentType.JEONSE,
                LocalDate.of(2026, 8, 20), null, 30000L));
        tradeRepository.saveAndFlush(trade(anseongRi, DealCategory.RENT, RentType.JEONSE,
                LocalDate.of(2026, 8, 20), null, 90000L));

        List<Long> inRange = search(ComplexSearchCondition.builder().rentType(RentType.JEONSE)
                .amountMin(25000L).amountMax(35000L).sort(SortCondition.LATEST).build());
        List<Long> saleInRange = search(ComplexSearchCondition.builder().dealCategory(DealCategory.SALE)
                .amountMin(25000L).amountMax(35000L).sort(SortCondition.LATEST).build());

        assertThat(inRange).containsExactly(anseongDong.getComplexId());
        assertThat(saleInRange).isEmpty();
    }

    @Test
    void 단지_검색은_search_log를_남기지_않는다() throws Exception {
        long before = searchLogRepository.count();

        mockMvc.perform(get("/api/complexes/search").param("keyword", "안성시").param("regionCode", "4155000000"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.length()").value(2));

        assertThat(searchLogRepository.count()).isEqualTo(before);
    }

    @Test
    void 검색_기록_API는_trim한_검색어를_한_건_남기고_인기검색어_집계에_반영된다() throws Exception {
        mockMvc.perform(post("/api/search/logs").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"keyword\":\"  안성시 \"}"))
                .andExpect(status().isOk());

        assertThat(searchLogRepository.findAll()).extracting("keyword").containsExactly("안성시");
        assertThat(searchLogRepository.findTopKeywordsSince(java.time.LocalDateTime.now().minusDays(1),
                PageRequest.of(0, 5))).containsExactly("안성시");
    }

    @Test
    void 검색_API의_형식_오류는_400이다() throws Exception {
        mockMvc.perform(get("/api/complexes/search").param("regionCode", "41110"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error.code").value("INVALID_REGION_CODE"));
        mockMvc.perform(get("/api/complexes/search").param("keyword", "가".repeat(51)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error.code").value("INVALID_SEARCH_KEYWORD"));
    }
}
