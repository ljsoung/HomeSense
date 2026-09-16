package com.jiseong.homesense.batch.matcher;

import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.MariaDBContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import com.jiseong.homesense.batch.loader.DedupHashCalculator;
import com.jiseong.homesense.batch.matcher.TradeRematchBatchProcessor.BatchOutcome;
import com.jiseong.homesense.batch.parser.dto.TradeDraft;
import com.jiseong.homesense.trade.entity.DealCategory;
import com.jiseong.homesense.trade.entity.HousingType;
import com.jiseong.homesense.trade.entity.Trade;
import com.jiseong.homesense.trade.repository.TradeRepository;

/**
 * {@code TradeRematchBatchProcessorTest}(Mockito)는 "충돌 시 findByDedupHash/deleteById가 정확한
 * 인자로 호출되는지"만 증명하고, "실제 UNIQUE 제약을 가진 DB 위에서 두 행이 정말 하나로 합쳐지고
 * 살아남는 쪽의 데이터가 훼손되지 않는지"는 증명하지 못한다 — 이 프로젝트가 이미 여러 번 겪은
 * "UNIQUE 제약 동시성/데이터 변형 로직은 Mockito로 검증 불가" 원칙(CLAUDE.md 참고)이 정확히 겨냥하는
 * 종류의 코드다. 특히 이 로직은 행을 **삭제**하는 뮤테이션이라 실 DB로 확인해 두지 않으면 다음에
 * 재사용할 때(다른 프로젝트든 재발 상황이든) 안전망 없이 또 대량 삭제를 하게 된다.
 *
 * <p>스키마는 {@code TradeChunkLoaderMariaDbIT}와 같은 최소 스키마를 재사용한다
 * (trade-race-schema.sql) — repairDedupHashBatch()는 complex_id가 null이어도(=UNMATCHED 식별자로도)
 * 동작하므로 complex/legal_district_code 행을 채울 필요가 없다.
 *
 * <p>Docker가 필요해 기본 `./gradlew test`에서는 제외되고 `./gradlew integrationTest`로만 실행된다.
 */
@SpringBootTest
@Testcontainers
@Tag("integration")
class TradeRematchBatchProcessorMariaDbIT {

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
        registry.add("spring.sql.init.schema-locations", () -> "classpath:testcontainers/trade-race-schema.sql");
    }

    @Autowired
    private TradeRematchBatchProcessor batchProcessor;
    @Autowired
    private TradeRepository tradeRepository;
    @Autowired
    private DedupHashCalculator dedupHashCalculator;

    /**
     * 같은 실거래를 나타내는 두 draft(UNMATCHED 식별자 — sggCd/umdNm/buildingName/jibun까지 전부
     * 동일)를 만든다. 실제 시나리오 재현: 09-14(구버전 매처) 최초 수집 때 UNMATCHED로 적재된 행의
     * dedup_hash가 그대로 stale하게 남은 상태에서, 09-15(신버전 매처) 재수집이 같은 거래를 이미
     * 올바른(=지금 이 identity로 계산되는) 해시로 별도 행에 적재해 버린 상황.
     */
    private static TradeDraft identity(LocalDate registrationDate) {
        return new TradeDraft(
                HousingType.APT, DealCategory.SALE, null, "15126469", "11680", "역삼동", "역삼래미안",
                "123-4", new BigDecimal("84.99"), (short) 10, (short) 2005, LocalDate.of(2024, 1, 15),
                120000L, null, null, null, "AGENT", "강남구", registrationDate, null, null, null,
                false, null, null, null, null, null);
    }

    @Test
    void 재계산된_해시가_이미_다른_행과_충돌하면_stale_행을_삭제하고_target_행은_그대로_보존한다() {
        String correctHash = dedupHashCalculator.calculate(identity(LocalDate.of(2024, 1, 20)));

        Trade target = tradeRepository.saveAndFlush(Trade.builder()
                .housingType(HousingType.APT).dealCategory(DealCategory.SALE)
                .datasetId("15126469").sggCd("11680").umdNm("역삼동")
                .buildingName("역삼래미안").jibun("123-4")
                .excluUseArea(new BigDecimal("84.99")).floor((short) 10).buildYear((short) 2005)
                .dealDate(LocalDate.of(2024, 1, 15)).dealAmount(120000L)
                .registrationDate(LocalDate.of(2024, 1, 20))
                .cancelYn(false)
                .dedupHash(correctHash)
                .createdAt(LocalDateTime.now()).updatedAt(LocalDateTime.now())
                .build());

        Trade stale = tradeRepository.saveAndFlush(Trade.builder()
                .housingType(HousingType.APT).dealCategory(DealCategory.SALE)
                .datasetId("15126469").sggCd("11680").umdNm("역삼동")
                .buildingName("역삼래미안").jibun("123-4")
                .excluUseArea(new BigDecimal("84.99")).floor((short) 10).buildYear((short) 2005)
                .dealDate(LocalDate.of(2024, 1, 15)).dealAmount(120000L)
                .cancelYn(false)
                .dedupHash("stale-pre-fix-unmatched-marker-hash")
                .createdAt(LocalDateTime.now()).updatedAt(LocalDateTime.now())
                .build());

        BatchOutcome outcome = batchProcessor.repairDedupHashBatch(0L);

        assertThat(outcome.hasMore()).isTrue();
        assertThat(outcome.changed()).isEqualTo(1);
        assertThat(outcome.unchanged()).isEqualTo(1);

        // stale 행은 삭제됐어야 한다.
        assertThat(tradeRepository.findById(stale.getTradeId())).isEmpty();

        // target 행은 살아남고, 그 데이터(특히 dedup_hash와 registration_date)는 훼손되지 않았다.
        Optional<Trade> survivor = tradeRepository.findById(target.getTradeId());
        assertThat(survivor).isPresent();
        assertThat(survivor.get().getDedupHash()).isEqualTo(correctHash);
        assertThat(survivor.get().getRegistrationDate()).isEqualTo(LocalDate.of(2024, 1, 20));

        // dedup_hash UNIQUE 제약상, 그리고 실제로도 이 identity를 가진 행은 정확히 하나만 남아야 한다.
        List<Trade> all = tradeRepository.findAll();
        assertThat(all).hasSize(1);
        assertThat(all.get(0).getDedupHash()).isEqualTo(correctHash);
    }
}
