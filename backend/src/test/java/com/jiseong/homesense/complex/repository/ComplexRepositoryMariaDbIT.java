package com.jiseong.homesense.complex.repository;

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
import org.springframework.data.domain.Pageable;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.MariaDBContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import com.jiseong.homesense.complex.dto.BoundsCondition;
import com.jiseong.homesense.complex.dto.ComplexSearchCondition;
import com.jiseong.homesense.complex.dto.ComplexSummaryResponse;
import com.jiseong.homesense.complex.dto.MapFilterCondition;
import com.jiseong.homesense.complex.dto.SortCondition;
import com.jiseong.homesense.complex.entity.Complex;
import com.jiseong.homesense.trade.entity.DealCategory;
import com.jiseong.homesense.trade.entity.HousingType;
import com.jiseong.homesense.trade.entity.Trade;
import com.jiseong.homesense.trade.repository.TradeRepository;

/**
 * ComplexServiceTest는 ComplexRepository 자체를 목킹해 "Service가 Repository를 올바르게 호출하는지"만
 * 증명한다 — ComplexRepositoryCustomImpl의 실제 QueryDSL 쿼리(검색조건에 맞는 거래 중 가장 최근 1건을
 * 상관 서브쿼리로 골라내는 로직, 정렬, dealCategory에 따라 dealAmount/depositAmount 중 무엇을 볼지
 * 갈리는 분기, 지도 범위 EXISTS 서브쿼리)가 실제로 맞는 결과를 내는지는 전혀 검증하지 못한다 — 이런
 * 종류의 버그는 목으로는 절대 드러나지 않는다(이 세션에서 반복 확인된 원칙, CLAUDE.md 참고).
 *
 * <p>Docker가 필요해 기본 `./gradlew test`에서는 제외되고 `./gradlew integrationTest`로만 실행된다.
 */
@SpringBootTest
@Testcontainers
@Tag("integration")
class ComplexRepositoryMariaDbIT {

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
    private ComplexRepository complexRepository;
    @Autowired
    private TradeRepository tradeRepository;

    private Complex complexA;
    private Complex complexB;
    private Complex complexCancelledOnly;
    private Complex complexRentD;

    @BeforeEach
    void setUp() {
        complexA = complexRepository.saveAndFlush(complex("A", "서울특별시", "강남구", "역삼동"));
        complexB = complexRepository.saveAndFlush(complex("B", "서울특별시", "강남구", "역삼동"));
        complexCancelledOnly = complexRepository.saveAndFlush(complex("C", "서울특별시", "강남구", "역삼동"));
        complexRentD = complexRepository.saveAndFlush(complex("D", "부산광역시", "해운대구", "우동"));

        // A: 오래된 거래(1/1)와 최근 거래(2/1) — 대표 거래는 반드시 2/1(더 최근)이어야 한다.
        tradeRepository.saveAndFlush(
                trade(complexA, HousingType.APT, DealCategory.SALE, LocalDate.of(2026, 1, 1), 50000L, null, "59.90", false));
        tradeRepository.saveAndFlush(
                trade(complexA, HousingType.APT, DealCategory.SALE, LocalDate.of(2026, 2, 1), 80000L, null, "84.90", false));
        // B: 거래 1건.
        tradeRepository.saveAndFlush(
                trade(complexB, HousingType.APT, DealCategory.SALE, LocalDate.of(2026, 1, 15), 60000L, null, "70.00", false));
        // C: 취소된 거래만 있음 — 검색 결과에서 완전히 제외돼야 한다.
        tradeRepository.saveAndFlush(
                trade(complexCancelledOnly, HousingType.APT, DealCategory.SALE, LocalDate.of(2026, 1, 20), 999999L, null, "999.00", true));
        // D: 전월세 — amount 필터/정렬은 dealAmount가 아니라 depositAmount를 봐야 한다.
        tradeRepository.saveAndFlush(
                trade(complexRentD, HousingType.APT, DealCategory.RENT, LocalDate.of(2026, 1, 20), null, 30000L, "50.00", false));
    }

    private static Complex complex(String name, String sido, String sigungu, String dongRi) {
        return Complex.builder()
                .sourceComplexCd("SRC-" + name)
                .complexName("단지" + name)
                .complexType("아파트")
                .sido(sido)
                .sigungu(sigungu)
                .dongRi(dongRi)
                .elevatorPassengerCount((short) 1)
                .elevatorCargoCount((short) 0)
                .elevatorCombinedCount((short) 0)
                .dataUpdatedAt(LocalDate.of(2026, 1, 1))
                .build();
    }

    private static Trade trade(Complex complex, HousingType housingType, DealCategory dealCategory,
            LocalDate dealDate, Long dealAmount, Long depositAmount, String area, boolean cancelYn) {
        return Trade.builder()
                .housingType(housingType)
                .dealCategory(dealCategory)
                .datasetId("15126468")
                .sggCd("11680")
                .complex(complex)
                .excluUseArea(new BigDecimal(area))
                .dealDate(dealDate)
                .dealAmount(dealAmount)
                .depositAmount(depositAmount)
                .cancelYn(cancelYn)
                .dedupHash("hash-" + complex.getComplexId() + "-" + dealDate + "-" + area)
                .build();
    }

    @Test
    void 검색결과의_대표거래는_취소되지_않은_거래_중_가장_최근이다() {
        ComplexSearchCondition condition = new ComplexSearchCondition(
                List.of(HousingType.APT), DealCategory.SALE, null, null, null, null, null, null,
                null, null, null, SortCondition.LATEST);

        Page<ComplexSummaryResponse> page = complexRepository.search(condition, PageRequest.of(0, 10));

        ComplexSummaryResponse resultA = page.getContent().stream()
                .filter(r -> r.complexId().equals(complexA.getComplexId()))
                .findFirst().orElseThrow();
        assertThat(resultA.representativeDealDate()).isEqualTo(LocalDate.of(2026, 2, 1));
        assertThat(resultA.representativeAmount()).isEqualTo(80000L);
        assertThat(resultA.representativeArea()).isEqualByComparingTo("84.90");
    }

    @Test
    void 대표거래_후보가_같은_날짜로_동률이어도_단지당_정확히_한_행만_나온다() {
        // 같은 단지에 dealDate가 완전히 같은 거래 두 건 — MAX(dealDate)만 걸었다면 이 단지가
        // 검색 결과에 두 행(카드 두 장)으로 중복 노출됐을 결함(코드리뷰에서 지적됨).
        Complex tieWithinSameComplex = complexRepository.saveAndFlush(complex("TIE-SAME", "서울특별시", "강남구", "역삼동"));
        LocalDate tiedDate = LocalDate.of(2026, 1, 10);
        tradeRepository.saveAndFlush(trade(tieWithinSameComplex, HousingType.APT, DealCategory.SALE,
                tiedDate, 50000L, null, "59.90", false));
        Trade laterInsertedTrade = tradeRepository.saveAndFlush(trade(tieWithinSameComplex, HousingType.APT,
                DealCategory.SALE, tiedDate, 60000L, null, "70.00", false));

        ComplexSearchCondition condition = new ComplexSearchCondition(
                List.of(HousingType.APT), DealCategory.SALE, null, null, null, null, null, null,
                "서울특별시", "강남구", "역삼동", SortCondition.LATEST);

        Page<ComplexSummaryResponse> page = complexRepository.search(condition, PageRequest.of(0, 10));

        List<ComplexSummaryResponse> matches = page.getContent().stream()
                .filter(r -> r.complexId().equals(tieWithinSameComplex.getComplexId()))
                .toList();
        assertThat(matches).hasSize(1);
        // tie-break는 MAX(trade_id) — 나중에 저장된(=더 큰 trade_id) 쪽이 대표거래가 된다.
        assertThat(matches.get(0).representativeAmount()).isEqualTo(laterInsertedTrade.getDealAmount());
    }

    @Test
    void 취소된_거래만_있는_단지는_검색결과에서_제외된다() {
        ComplexSearchCondition condition = new ComplexSearchCondition(
                List.of(HousingType.APT), DealCategory.SALE, null, null, null, null, null, null,
                null, null, null, SortCondition.LATEST);

        Page<ComplexSummaryResponse> page = complexRepository.search(condition, PageRequest.of(0, 10));

        assertThat(page.getContent()).extracting(ComplexSummaryResponse::complexId)
                .doesNotContain(complexCancelledOnly.getComplexId());
    }

    @Test
    void 최신순_금액순_면적순_모두_같은_대표거래_기준으로만_순서가_바뀐다() {
        ComplexSearchCondition base = new ComplexSearchCondition(
                List.of(HousingType.APT), DealCategory.SALE, null, null, null, null, null, null,
                null, null, null, SortCondition.LATEST);

        List<Long> latestOrder = ids(complexRepository.search(base, PageRequest.of(0, 10)));
        List<Long> amountOrder = ids(complexRepository.search(withSort(base, SortCondition.AMOUNT), PageRequest.of(0, 10)));
        List<Long> areaOrder = ids(complexRepository.search(withSort(base, SortCondition.AREA), PageRequest.of(0, 10)));

        // A(대표: 2/1, 80000, 84.90) vs B(대표: 1/15, 60000, 70.00) — 최신순은 A 먼저,
        // 금액순(오름차순)은 B 먼저, 면적순(내림차순)은 A 먼저.
        assertThat(latestOrder).containsExactly(complexA.getComplexId(), complexB.getComplexId());
        assertThat(amountOrder).containsExactly(complexB.getComplexId(), complexA.getComplexId());
        assertThat(areaOrder).containsExactly(complexA.getComplexId(), complexB.getComplexId());
    }

    @Test
    void 정렬_기준이_동률이어도_페이지_경계에서_단지가_중복되거나_누락되지_않는다() {
        // dealDate(일 단위)·금액·면적이 전부 동일한 대표거래를 가진 단지 4개 — SQL은 이런 동률 행의
        // 상대 순서를 보장하지 않으므로, complexId/tradeId 2차 정렬키가 없으면 offset/limit으로
        // 나눠 받는 인접 페이지에서 같은 단지가 중복되거나 빠질 수 있다(코드리뷰에서 지적됨).
        // A/B(setUp)와 섞이지 않도록 별도 지역에 만든다.
        LocalDate tiedDealDate = LocalDate.of(2026, 1, 1);
        List<Long> tiedComplexIds = new java.util.ArrayList<>();
        for (int i = 1; i <= 4; i++) {
            Complex tied = complexRepository.saveAndFlush(complex("TIE" + i, "대구광역시", "수성구", "범어동"));
            tradeRepository.saveAndFlush(trade(tied, HousingType.APT, DealCategory.SALE,
                    tiedDealDate, 50000L, null, "59.90", false));
            tiedComplexIds.add(tied.getComplexId());
        }

        ComplexSearchCondition condition = new ComplexSearchCondition(
                List.of(HousingType.APT), DealCategory.SALE, null, null, null, null, null, null,
                "대구광역시", "수성구", "범어동", SortCondition.LATEST);

        List<Long> page0 = ids(complexRepository.search(condition, PageRequest.of(0, 2)));
        List<Long> page1 = ids(complexRepository.search(condition, PageRequest.of(1, 2)));
        // 같은 조건을 다시 조회해도 완전히 같은 순서가 나와야 한다 — 결정적 정렬이라는 뜻이다.
        List<Long> page0Again = ids(complexRepository.search(condition, PageRequest.of(0, 2)));

        assertThat(page0).hasSize(2);
        assertThat(page1).hasSize(2);
        assertThat(page0).doesNotContainAnyElementsOf(page1);
        assertThat(page0).containsExactlyElementsOf(page0Again);

        List<Long> combined = new java.util.ArrayList<>(page0);
        combined.addAll(page1);
        assertThat(combined).containsExactlyInAnyOrderElementsOf(tiedComplexIds);
    }

    @Test
    void RENT는_dealAmount가_아니라_depositAmount로_필터링된다() {
        ComplexSearchCondition condition = new ComplexSearchCondition(
                null, DealCategory.RENT, null, null, 20000L, 40000L, null, null,
                null, null, null, SortCondition.LATEST);

        Page<ComplexSummaryResponse> page = complexRepository.search(condition, PageRequest.of(0, 10));

        assertThat(page.getContent()).hasSize(1);
        assertThat(page.getContent().get(0).complexId()).isEqualTo(complexRentD.getComplexId());
        assertThat(page.getContent().get(0).representativeAmount()).isEqualTo(30000L);
    }

    @Test
    void 건축년도_필터는_trade_build_year가_아니라_complex_approval_date_기준이다() {
        // approval_date=2020(범위 안)인데 그 단지의 유일한 거래는 build_year=1990(범위 밖)으로
        // 어긋나 있다 — trade.build_year를 기준으로 삼았다면 이 단지는 걸러졌을 것이다.
        Complex mismatchedButInRange = complexRepository.saveAndFlush(
                Complex.builder().sourceComplexCd("SRC-MISMATCH").complexName("승인일다른단지")
                        .complexType("아파트").approvalDate(LocalDate.of(2020, 6, 1))
                        .elevatorPassengerCount((short) 1).elevatorCargoCount((short) 0).elevatorCombinedCount((short) 0)
                        .dataUpdatedAt(LocalDate.of(2026, 1, 1)).build());
        tradeRepository.saveAndFlush(Trade.builder()
                .housingType(HousingType.APT).dealCategory(DealCategory.SALE)
                .datasetId("15126468").sggCd("11680").complex(mismatchedButInRange)
                .excluUseArea(new BigDecimal("59.90")).buildYear((short) 1990)
                .dealDate(LocalDate.of(2026, 1, 1)).dealAmount(50000L).cancelYn(false)
                .dedupHash("hash-mismatch").build());

        // approval_date=2010(범위 밖) — build_year 없이도 approval_date만으로 제외돼야 한다.
        Complex outOfRange = complexRepository.saveAndFlush(
                Complex.builder().sourceComplexCd("SRC-OUT-OF-RANGE").complexName("범위밖승인일단지")
                        .complexType("아파트").approvalDate(LocalDate.of(2010, 1, 1))
                        .elevatorPassengerCount((short) 1).elevatorCargoCount((short) 0).elevatorCombinedCount((short) 0)
                        .dataUpdatedAt(LocalDate.of(2026, 1, 1)).build());
        tradeRepository.saveAndFlush(Trade.builder()
                .housingType(HousingType.APT).dealCategory(DealCategory.SALE)
                .datasetId("15126468").sggCd("11680").complex(outOfRange)
                .excluUseArea(new BigDecimal("59.90")).buildYear((short) 2020)
                .dealDate(LocalDate.of(2026, 1, 1)).dealAmount(50000L).cancelYn(false)
                .dedupHash("hash-out-of-range").build());

        ComplexSearchCondition condition = new ComplexSearchCondition(
                null, null, null, null, null, null, (short) 2019, (short) 2021,
                null, null, null, SortCondition.LATEST);

        Page<ComplexSummaryResponse> page = complexRepository.search(condition, PageRequest.of(0, 10));

        assertThat(page.getContent()).extracting(ComplexSummaryResponse::complexId)
                .contains(mismatchedButInRange.getComplexId())
                .doesNotContain(outOfRange.getComplexId());
    }

    @Test
    void 지역_필터는_시도_시군구_동리가_모두_일치하는_단지만_반환한다() {
        ComplexSearchCondition condition = new ComplexSearchCondition(
                null, null, null, null, null, null, null, null,
                "부산광역시", "해운대구", "우동", SortCondition.LATEST);

        Page<ComplexSummaryResponse> page = complexRepository.search(condition, PageRequest.of(0, 10));

        assertThat(page.getContent()).extracting(ComplexSummaryResponse::complexId)
                .containsExactly(complexRentD.getComplexId());
    }

    @Test
    void 지도_범위_조회는_bounds_안에_있고_housingType_필터를_만족하는_단지만_반환한다() {
        Complex insideBounds = complexRepository.saveAndFlush(
                Complex.builder().sourceComplexCd("SRC-IN").complexName("범위안")
                        .complexType("아파트").latitude(new BigDecimal("37.50")).longitude(new BigDecimal("127.05"))
                        .elevatorPassengerCount((short) 1).elevatorCargoCount((short) 0).elevatorCombinedCount((short) 0)
                        .dataUpdatedAt(LocalDate.of(2026, 1, 1)).build());
        tradeRepository.saveAndFlush(trade(insideBounds, HousingType.APT, DealCategory.SALE,
                LocalDate.of(2026, 1, 1), 50000L, null, "59.90", false));
        Complex outsideBounds = complexRepository.saveAndFlush(
                Complex.builder().sourceComplexCd("SRC-OUT").complexName("범위밖")
                        .complexType("아파트").latitude(new BigDecimal("38.90")).longitude(new BigDecimal("127.05"))
                        .elevatorPassengerCount((short) 1).elevatorCargoCount((short) 0).elevatorCombinedCount((short) 0)
                        .dataUpdatedAt(LocalDate.of(2026, 1, 1)).build());

        BoundsCondition bounds = new BoundsCondition(
                new BigDecimal("37.0"), new BigDecimal("126.5"), new BigDecimal("38.0"), new BigDecimal("127.5"));

        List<Complex> results = complexRepository.searchInBounds(bounds, new MapFilterCondition(null), 501);

        assertThat(results).extracting(Complex::getComplexId).contains(insideBounds.getComplexId());
        assertThat(results).extracting(Complex::getComplexId).doesNotContain(outsideBounds.getComplexId());
    }

    @Test
    void 최근_등록_단지_조회는_data_updated_at이_전부_같아도_complex_id_내림차순으로_정상_동작한다() {
        // 단지 기본정보는 xlsx 파일 1건을 통째로 적재해 실제로는 거의 모든 row가 같은
        // data_updated_at(원본 파일 기준일)을 갖는다(코드리뷰에서 지적됨) — 그 상황을 그대로
        // 재현하기 위해 두 fixture 모두 동일한 dataUpdatedAt을 준다. 그래도 complex_id(서로게이트
        // PK, AUTO_INCREMENT)는 항상 고유하므로 동점 없이 등록 순서를 가려낼 수 있어야 한다.
        LocalDate sameSnapshotDate = LocalDate.of(2026, 8, 7);
        Complex registeredFirst = complexRepository.saveAndFlush(
                Complex.builder().sourceComplexCd("SRC-FIRST").complexName("먼저등록")
                        .complexType("아파트")
                        .elevatorPassengerCount((short) 1).elevatorCargoCount((short) 0).elevatorCombinedCount((short) 0)
                        .dataUpdatedAt(sameSnapshotDate).build());
        Complex registeredLast = complexRepository.saveAndFlush(
                Complex.builder().sourceComplexCd("SRC-LAST").complexName("나중등록")
                        .complexType("아파트")
                        .elevatorPassengerCount((short) 1).elevatorCargoCount((short) 0).elevatorCombinedCount((short) 0)
                        .dataUpdatedAt(sameSnapshotDate).build());

        List<Complex> topByRecentRegistration = complexRepository.findAllByOrderByComplexIdDesc(PageRequest.of(0, 1));

        assertThat(topByRecentRegistration).hasSize(1);
        assertThat(topByRecentRegistration.get(0).getComplexId()).isEqualTo(registeredLast.getComplexId());
        assertThat(topByRecentRegistration.get(0).getComplexId()).isNotEqualTo(registeredFirst.getComplexId());
    }

    @Test
    void 인기단지_후보는_취소되지_않은_최근_거래량이_많은_순이다() {
        // complexB에 거래 2건을 추가해(setUp의 1건 + 여기 2건 = 3건) complexA(2건)보다 많게 만든다.
        tradeRepository.saveAndFlush(trade(complexB, HousingType.APT, DealCategory.SALE,
                LocalDate.of(2026, 1, 16), 61000L, null, "70.00", false));
        tradeRepository.saveAndFlush(trade(complexB, HousingType.APT, DealCategory.SALE,
                LocalDate.of(2026, 1, 17), 62000L, null, "70.00", false));

        Pageable top2 = PageRequest.of(0, 2);
        List<Long> topIds = tradeRepository.findTopComplexIdsByRecentTradeVolume(LocalDate.of(2025, 1, 1), top2);

        assertThat(topIds).hasSize(2);
        assertThat(topIds.get(0)).isEqualTo(complexB.getComplexId());
    }

    private static List<Long> ids(Page<ComplexSummaryResponse> page) {
        return page.getContent().stream().map(ComplexSummaryResponse::complexId).toList();
    }

    private static ComplexSearchCondition withSort(ComplexSearchCondition base, SortCondition sort) {
        return new ComplexSearchCondition(base.housingTypes(), base.dealCategory(), base.areaMin(), base.areaMax(),
                base.amountMin(), base.amountMax(), base.buildYearMin(), base.buildYearMax(), base.sido(),
                base.sigungu(), base.dongRi(), sort);
    }
}
