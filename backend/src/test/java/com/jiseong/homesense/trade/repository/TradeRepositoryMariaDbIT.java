package com.jiseong.homesense.trade.repository;

import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.transaction.annotation.Transactional;
import org.testcontainers.containers.MariaDBContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import com.jiseong.homesense.complex.entity.Complex;
import com.jiseong.homesense.complex.repository.ComplexRepository;
import com.jiseong.homesense.region.entity.LegalDistrictCode;
import com.jiseong.homesense.region.repository.LegalDistrictCodeRepository;
import com.jiseong.homesense.trade.dto.DealTypeFilter;
import com.jiseong.homesense.trade.dto.TradeSearchCondition;
import com.jiseong.homesense.trade.dto.TradeSortCondition;
import com.jiseong.homesense.trade.dto.TradeSummaryResponse;
import com.jiseong.homesense.trade.entity.DealCategory;
import com.jiseong.homesense.trade.entity.HousingType;
import com.jiseong.homesense.trade.entity.RentType;
import com.jiseong.homesense.trade.entity.Trade;

/**
 * TradeServiceTest는 TradeRepository 자체를 목킹해 "Service가 Repository를 올바르게 호출하는지"만
 * 증명한다 — TradeRepositoryCustomImpl의 실제 QueryDSL 쿼리(legalDongCd/housingType/dealCategory
 * 동적 필터, dealCategory에 따라 dealAmount/depositAmount 중 무엇을 볼지 갈리는 분기, 정렬, 취소
 * 거래 제외, findHistory()의 취소 거래 포함 여부)가 실제로 맞는 결과를 내는지는 전혀 검증하지
 * 못한다 — 이런 종류의 버그는 목으로는 절대 드러나지 않는다(ComplexRepositoryMariaDbIT와 같은 원칙,
 * CLAUDE.md 참고).
 *
 * <p>Docker가 필요해 기본 {@code ./gradlew test}에서는 제외되고 {@code ./gradlew integrationTest}로만
 * 실행된다.
 *
 * <p>{@code @Transactional}이 반드시 필요하다 — {@code @SpringBootTest}는 클래스 안의 모든 테스트
 * 메서드가 같은(캐시된) ApplicationContext, 즉 같은 Testcontainers MariaDB 인스턴스를 공유한다.
 * {@code @BeforeEach}의 {@code saveAndFlush()}는 그 자체로 자기완결 트랜잭션이라 즉시 커밋되므로,
 * 이 애노테이션 없이는 두 번째 테스트 메서드부터 setUp()이 같은 source_complex_cd/legal_dong_cd를
 * 다시 삽입하려다 UNIQUE 위반으로 실패한다(ComplexRepositoryMariaDbIT가 정확히 이 함정을 문서화함).
 */
@SpringBootTest
@Testcontainers
@Transactional
@Tag("integration")
class TradeRepositoryMariaDbIT {

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
        registry.add("spring.sql.init.schema-locations", () -> "classpath:testcontainers/trade-search-schema.sql");
    }

    @Autowired
    private TradeRepository tradeRepository;
    @Autowired
    private ComplexRepository complexRepository;
    @Autowired
    private LegalDistrictCodeRepository legalDistrictCodeRepository;

    private LegalDistrictCode yeoksam;
    private LegalDistrictCode haeundae;
    private Complex complexA;

    @BeforeEach
    void setUp() {
        yeoksam = legalDistrictCodeRepository.saveAndFlush(legalDistrictCode("1168010100", "서울특별시", "강남구", "역삼동"));
        haeundae = legalDistrictCodeRepository.saveAndFlush(legalDistrictCode("2635010400", "부산광역시", "해운대구", "우동"));
        complexA = complexRepository.saveAndFlush(complex("A"));
    }

    private static LegalDistrictCode legalDistrictCode(String cd, String sido, String sigungu, String dong) {
        return LegalDistrictCode.builder()
                .legalDongCd(cd)
                .legalDongName(sido + " " + sigungu + " " + dong)
                .sidoName(sido)
                .sigunguName(sigungu)
                .eupmyeondongName(dong)
                .isActive(true)
                .dataVersion(LocalDate.of(2026, 1, 1))
                .build();
    }

    private static Complex complex(String name) {
        return Complex.builder()
                .sourceComplexCd("SRC-" + name)
                .complexName("단지" + name)
                .complexType("아파트")
                .elevatorPassengerCount((short) 1)
                .elevatorCargoCount((short) 0)
                .elevatorCombinedCount((short) 0)
                .dataUpdatedAt(LocalDate.of(2026, 1, 1))
                .build();
    }

    private Trade.TradeBuilder baseTrade() {
        return Trade.builder()
                .housingType(HousingType.APT)
                .dealCategory(DealCategory.SALE)
                .datasetId("15126468")
                .sggCd("11680")
                .legalDistrictCode(yeoksam)
                .complex(complexA)
                .excluUseArea(new BigDecimal("59.90"))
                .dealDate(LocalDate.of(2026, 1, 1))
                .dealAmount(50000L)
                .cancelYn(false);
    }

    @Test
    void search_legalDongCd로_필터링한다() {
        Trade inYeoksam = tradeRepository.saveAndFlush(baseTrade().dedupHash("h1").build());
        Trade inHaeundae = tradeRepository.saveAndFlush(baseTrade()
                .legalDistrictCode(haeundae).dedupHash("h2").build());

        TradeSearchCondition condition = new TradeSearchCondition(
                yeoksam.getLegalDongCd(), null, null, null, null, null, null, null, TradeSortCondition.LATEST);

        Page<TradeSummaryResponse> page = tradeRepository.search(condition, PageRequest.of(0, 10));

        assertThat(page.getContent()).extracting(TradeSummaryResponse::tradeId)
                .contains(inYeoksam.getTradeId())
                .doesNotContain(inHaeundae.getTradeId());
    }

    @Test
    void search_취소된_거래는_결과에서_제외한다() {
        Trade cancelled = tradeRepository.saveAndFlush(baseTrade().cancelYn(true).dedupHash("h-cancelled").build());
        Trade active = tradeRepository.saveAndFlush(baseTrade().dedupHash("h-active").build());

        TradeSearchCondition condition = new TradeSearchCondition(
                null, null, null, null, null, null, null, null, TradeSortCondition.LATEST);

        Page<TradeSummaryResponse> page = tradeRepository.search(condition, PageRequest.of(0, 10));

        assertThat(page.getContent()).extracting(TradeSummaryResponse::tradeId)
                .contains(active.getTradeId())
                .doesNotContain(cancelled.getTradeId());
    }

    @Test
    void search_RENT는_dealAmount가_아니라_depositAmount로_필터링된다() {
        Trade rent = tradeRepository.saveAndFlush(baseTrade()
                .dealCategory(DealCategory.RENT).rentType(RentType.JEONSE)
                .dealAmount(null).depositAmount(30000L).dedupHash("h-rent").build());
        Trade sale = tradeRepository.saveAndFlush(baseTrade().dedupHash("h-sale").build());

        TradeSearchCondition condition = new TradeSearchCondition(
                null, null, DealCategory.RENT, null, null, null, 20000L, 40000L, TradeSortCondition.LATEST);

        Page<TradeSummaryResponse> page = tradeRepository.search(condition, PageRequest.of(0, 10));

        assertThat(page.getContent()).extracting(TradeSummaryResponse::tradeId)
                .containsExactly(rent.getTradeId());
        assertThat(page.getContent().get(0).amount()).isEqualTo(30000L);
        assertThat(page.getContent()).extracting(TradeSummaryResponse::tradeId).doesNotContain(sale.getTradeId());
    }

    @Test
    void search_금액순은_오름차순_면적순은_내림차순으로_정렬한다() {
        Trade cheaper = tradeRepository.saveAndFlush(baseTrade()
                .dealAmount(30000L).excluUseArea(new BigDecimal("40.00")).dedupHash("h-cheap").build());
        Trade pricier = tradeRepository.saveAndFlush(baseTrade()
                .dealAmount(90000L).excluUseArea(new BigDecimal("100.00")).dedupHash("h-pricey").build());

        TradeSearchCondition byAmount = new TradeSearchCondition(
                null, null, null, null, null, null, null, null, TradeSortCondition.AMOUNT);
        TradeSearchCondition byArea = new TradeSearchCondition(
                null, null, null, null, null, null, null, null, TradeSortCondition.AREA);

        assertThat(ids(tradeRepository.search(byAmount, PageRequest.of(0, 10))))
                .containsExactly(cheaper.getTradeId(), pricier.getTradeId());
        assertThat(ids(tradeRepository.search(byArea, PageRequest.of(0, 10))))
                .containsExactly(pricier.getTradeId(), cheaper.getTradeId());
    }

    @Test
    void search_매칭_성공한_거래는_지역과_단지정보를_함께_노출한다() {
        Trade t = tradeRepository.saveAndFlush(baseTrade().dedupHash("h-matched").build());

        Page<TradeSummaryResponse> page = tradeRepository.search(
                new TradeSearchCondition(null, null, null, null, null, null, null, null, TradeSortCondition.LATEST),
                PageRequest.of(0, 10));

        TradeSummaryResponse result = page.getContent().stream()
                .filter(r -> r.tradeId().equals(t.getTradeId())).findFirst().orElseThrow();
        assertThat(result.complexId()).isEqualTo(complexA.getComplexId());
        assertThat(result.complexName()).isEqualTo("단지A");
        assertThat(result.sido()).isEqualTo("서울특별시");
        assertThat(result.sigungu()).isEqualTo("강남구");
        assertThat(result.dongRi()).isEqualTo("역삼동");
    }

    @Test
    void search_단지_매칭에_실패한_거래도_결과에서_빠지지_않는다() {
        Trade unmatched = tradeRepository.saveAndFlush(baseTrade()
                .complex(null).legalDistrictCode(null).buildingName("무매칭빌라").dedupHash("h-unmatched").build());

        Page<TradeSummaryResponse> page = tradeRepository.search(
                new TradeSearchCondition(null, null, null, null, null, null, null, null, TradeSortCondition.LATEST),
                PageRequest.of(0, 10));

        TradeSummaryResponse result = page.getContent().stream()
                .filter(r -> r.tradeId().equals(unmatched.getTradeId())).findFirst().orElseThrow();
        assertThat(result.complexId()).isNull();
        assertThat(result.sido()).isNull();
        assertThat(result.buildingName()).isEqualTo("무매칭빌라");
    }

    @Test
    void findHistory_complexId로_필터링하고_deal_date_내림차순으로_정렬한다() {
        Trade older = tradeRepository.saveAndFlush(baseTrade()
                .dealDate(LocalDate.of(2026, 1, 1)).dedupHash("h-older").build());
        Trade newer = tradeRepository.saveAndFlush(baseTrade()
                .dealDate(LocalDate.of(2026, 2, 1)).dedupHash("h-newer").build());
        Complex complexB = complexRepository.saveAndFlush(complex("B"));
        tradeRepository.saveAndFlush(baseTrade().complex(complexB).dedupHash("h-other-complex").build());

        List<Trade> history = tradeRepository.findHistory(complexA.getComplexId(), null, DealTypeFilter.ALL);

        assertThat(history).extracting(Trade::getTradeId).containsExactly(newer.getTradeId(), older.getTradeId());
    }

    @Test
    void findHistory_취소된_거래도_결과에_포함한다() {
        Trade cancelled = tradeRepository.saveAndFlush(baseTrade().cancelYn(true).dedupHash("h-cancelled").build());

        List<Trade> history = tradeRepository.findHistory(complexA.getComplexId(), null, DealTypeFilter.ALL);

        assertThat(history).extracting(Trade::getTradeId).contains(cancelled.getTradeId());
    }

    @Test
    void findHistory_dealType으로_JEONSE와_WOLSE를_구분해_필터링한다() {
        Trade jeonse = tradeRepository.saveAndFlush(baseTrade()
                .dealCategory(DealCategory.RENT).rentType(RentType.JEONSE)
                .dealAmount(null).depositAmount(30000L).dedupHash("h-jeonse").build());
        Trade wolse = tradeRepository.saveAndFlush(baseTrade()
                .dealCategory(DealCategory.RENT).rentType(RentType.WOLSE)
                .dealAmount(null).depositAmount(1000L).monthlyRentAmount(50L).dedupHash("h-wolse").build());

        List<Trade> jeonseOnly = tradeRepository.findHistory(complexA.getComplexId(), null, DealTypeFilter.JEONSE);
        List<Trade> wolseOnly = tradeRepository.findHistory(complexA.getComplexId(), null, DealTypeFilter.WOLSE);

        assertThat(jeonseOnly).extracting(Trade::getTradeId).containsExactly(jeonse.getTradeId());
        assertThat(wolseOnly).extracting(Trade::getTradeId).containsExactly(wolse.getTradeId());
    }

    @Test
    void findHistory_housingType으로_필터링한다() {
        Trade apt = tradeRepository.saveAndFlush(baseTrade().dedupHash("h-apt").build());
        Trade villa = tradeRepository.saveAndFlush(baseTrade().housingType(HousingType.VILLA).dedupHash("h-villa").build());

        List<Trade> aptOnly = tradeRepository.findHistory(complexA.getComplexId(), HousingType.APT, DealTypeFilter.ALL);

        assertThat(aptOnly).extracting(Trade::getTradeId)
                .contains(apt.getTradeId())
                .doesNotContain(villa.getTradeId());
    }

    @Test
    void findHistory_선택된_거래유형의_이력이_없으면_빈_리스트를_반환한다() {
        List<Trade> result = tradeRepository.findHistory(complexA.getComplexId(), null, DealTypeFilter.WOLSE);

        assertThat(result).isEmpty();
    }

    private static List<Long> ids(Page<TradeSummaryResponse> page) {
        return page.getContent().stream().map(TradeSummaryResponse::tradeId).toList();
    }
}
