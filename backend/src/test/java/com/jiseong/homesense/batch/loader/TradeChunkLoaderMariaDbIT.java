package com.jiseong.homesense.batch.loader;

import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.MariaDBContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import com.jiseong.homesense.batch.parser.dto.TradeDraft;
import com.jiseong.homesense.trade.entity.DealCategory;
import com.jiseong.homesense.trade.entity.HousingType;
import com.jiseong.homesense.trade.entity.Trade;
import com.jiseong.homesense.trade.repository.TradeRepository;

/**
 * TradeChunkLoaderTest의 upsert 반환값 테스트는 TradeRepository#upsert를 목킹해 "정확한 인자로
 * 호출된다"만 증명하고, "원자적 INSERT ... ON DUPLICATE KEY UPDATE가 실제 DB에서 올바르게 동작하는지"는
 * 증명하지 못한다. 이 테스트는 실제 MariaDB(Testcontainers) 위에서 이 네이티브 SQL 자체가 문법·바인딩
 * 오류 없이 동작하는지, 그리고 TradeChunkLoader.upsertOne()이 수정된 실제 버그 시나리오 — **같은 청크
 * 트랜잭션(단일 스레드) 안에서 같은 dedup_hash가 두 번 등장하는 경우**(data.go.kr 페이지네이션이 같은
 * 거래를 중복으로 돌려주는 경우 등, 실 배치 재실행에서 처리 대상의 2.2% 유실로 실측된 원인) — 를
 * 올바르게 처리하는지 검증한다.
 *
 * <p>**두 스레드의 진짜 동시 경쟁은 의도적으로 테스트하지 않는다.** BAT-SCH-01(BatchExecutionOrchestrator)이
 * 시군구×계약월×주택유형×거래유형 조합을 항상 단일 스레드로 순회하므로(CLAUDE.md "REQUIRES_NEW 격리
 * INSERT 게이트웨이 패턴" 절 참고) 이 파이프라인에 진짜 동시 쓰기는 없다. 실제로 두 스레드가 같은
 * dedup_hash로 진짜 동시에 INSERT ... ON DUPLICATE KEY UPDATE를 시도하게 만들면 InnoDB가 이를
 * 데드락으로 감지해 한쪽 트랜잭션 전체를 강제 종료시킬 수 있다(이 테스트를 처음 그렇게 작성했을 때
 * 실제로 UnexpectedRollbackException으로 재현됐다) — 원자적 upsert가 REPEATABLE READ 스냅샷 문제는
 * 없애지만, 진짜 동시 쓰기 상황에서의 데드락 위험까지 없애주지는 않는다. 이 위험은 배치가 24시간을
 * 넘겨 다음 cron과 겹치는 극히 드문 시나리오에서만 이론상 성립하고, 그 시나리오 자체가 이미 별도로
 * "우선순위 낮음"으로 문서화돼 있어(CLAUDE.md 같은 절) 여기서 추가로 다루지 않는다.
 *
 * <p>스키마는 테이블정의서 8장 원문이 아니라 이 테스트가 실제로 건드리는 최소 부분집합을 재구성한
 * 것이다(testcontainers/trade-race-schema.sql) — 권위 있는 DDL은 여전히 테이블정의서 8장이다.
 *
 * <p>Docker가 필요해 기본 `./gradlew test`에서는 제외되고 `./gradlew integrationTest`로만 실행된다.
 */
@SpringBootTest
@Testcontainers
@Tag("integration")
class TradeChunkLoaderMariaDbIT {

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
    private TradeChunkLoader tradeChunkLoader;
    @Autowired
    private TradeRepository tradeRepository;
    @Autowired
    private DedupHashCalculator dedupHashCalculator;

    private static TradeDraft draft(LocalDate registrationDate, String aptDong) {
        return new TradeDraft(
                HousingType.APT, DealCategory.SALE, null, "15126468", "11680", "역삼동", "역삼래미안",
                "123-4", new BigDecimal("84.99"), (short) 10, (short) 2005, LocalDate.of(2024, 1, 15),
                120000L, null, null, aptDong, "AGENT", "강남구", registrationDate, null, null, null,
                false, null, null, null, null, null);
    }

    /**
     * dealDate/dealAmount가 달라 아래 두 draft와 절대 같은 dedup_hash를 만들지 않는 별개 건.
     */
    private static TradeDraft unrelatedDraft() {
        return new TradeDraft(
                HousingType.APT, DealCategory.SALE, null, "15126468", "11680", "역삼동", "역삼래미안",
                "123-4", new BigDecimal("84.99"), (short) 10, (short) 2005, LocalDate.of(2024, 2, 1),
                130000L, null, null, "103동", "AGENT", "강남구", LocalDate.of(2024, 2, 1), null, null, null,
                false, null, null, null, null, null);
    }

    @Test
    void 같은_청크에서_같은_dedup_hash가_두_번_나오면_두_번째는_첫_번째를_UPDATE하고_나머지_건도_함께_커밋된다() {
        // 실 배치 재실행에서 재현된 버그 시나리오 그대로: 단일 스레드가 한 청크 안에서 같은 거래를
        // (페이지네이션 중복 등으로) 두 번 만난다. 첫 번째는 INSERT, 두 번째는 그 자리에서 UPDATE로
        // 처리돼야 하고, 같은 청크의 무관한 세 번째 건도 함께 정상 커밋돼야 한다.
        TradeDraft first = draft(LocalDate.of(2024, 1, 20), "101동");
        TradeDraft duplicate = draft(LocalDate.of(2024, 1, 25), "102동");
        TradeDraft unrelated = unrelatedDraft();
        String expectedHash = dedupHashCalculator.calculate(first);
        assertThat(dedupHashCalculator.calculate(duplicate)).isEqualTo(expectedHash);
        assertThat(dedupHashCalculator.calculate(unrelated)).isNotEqualTo(expectedHash);

        ChunkOutcome outcome = tradeChunkLoader.loadChunk(List.of(first, duplicate, unrelated));

        assertThat(outcome.result().errorCount()).isZero();
        assertThat(outcome.result().processedCount()).isEqualTo(3);

        List<Trade> rows = tradeRepository.findAll();
        List<Trade> matched = rows.stream().filter(row -> row.getDedupHash().equals(expectedHash)).toList();
        // dedup_hash UNIQUE 제약상 정확히 한 행만 존재해야 하고, 그 값은 나중에 처리된 duplicate로
        // 갱신돼 있어야 한다(applyLateUpdate 대상 필드인 aptDong/registrationDate 확인).
        assertThat(matched).hasSize(1);
        assertThat(matched.get(0).getAptDong()).isEqualTo("102동");
        assertThat(matched.get(0).getRegistrationDate()).isEqualTo(LocalDate.of(2024, 1, 25));
        // 같은 청크의 무관한 건도 함께 커밋됐다는 증거.
        assertThat(rows).anySatisfy(row -> assertThat(row.getAptDong()).isEqualTo("103동"));
    }

    /**
     * Codex 코드리뷰 P1 지적(2026-09-16) 회귀 테스트: UNIQUE 경쟁이 아니라 **진짜 제약 위반**(여기서는
     * VARCHAR(100) 초과 문자열)이 청크 안 한 건에서 발생해도, 그 예외가 청크 트랜잭션 전체를
     * rollback-only로 표시해 나머지 건까지 함께 날려서는 안 된다 — {@link TradeUpsertGateway}의
     * REQUIRES_NEW 격리가 없었다면 이 테스트는 정상 건의 행이 하나도 커밋되지 않아 실패했을 것이다
     * (그 경우 loadChunk() 자체가 커밋 시점에 UnexpectedRollbackException을 던지며 실패한다).
     */
    @Test
    void 한_건이_컬럼_길이_제약을_위반해도_나머지_정상_건은_그대로_커밋된다() {
        String oversizedBuildingName = "가".repeat(150); // building_name VARCHAR(100) 초과
        TradeDraft violatesConstraint = new TradeDraft(
                HousingType.APT, DealCategory.SALE, null, "15126468", "11680", "삼성동", oversizedBuildingName,
                "500", new BigDecimal("59.99"), (short) 3, (short) 2010, LocalDate.of(2024, 3, 1),
                90000L, null, null, "201동", "AGENT", "강남구", LocalDate.of(2024, 3, 2), null, null, null,
                false, null, null, null, null, null);
        TradeDraft ok = new TradeDraft(
                HousingType.APT, DealCategory.SALE, null, "15126468", "11680", "삼성동", "정상아파트",
                "501", new BigDecimal("59.99"), (short) 4, (short) 2010, LocalDate.of(2024, 3, 1),
                91000L, null, null, "202동", "AGENT", "강남구", LocalDate.of(2024, 3, 2), null, null, null,
                false, null, null, null, null, null);
        String okHash = dedupHashCalculator.calculate(ok);

        ChunkOutcome outcome = tradeChunkLoader.loadChunk(List.of(violatesConstraint, ok));

        assertThat(outcome.result().errorCount()).isEqualTo(1);
        assertThat(outcome.result().processedCount()).isEqualTo(1);

        List<Trade> rows = tradeRepository.findAll();
        // 정상 건(jibun=501)은 커밋돼 있어야 한다.
        assertThat(rows).anySatisfy(row -> assertThat(row.getDedupHash()).isEqualTo(okHash));
        // 제약을 위반한 건(jibun=500)은 어떤 형태로도 저장되지 않았어야 한다.
        assertThat(rows).noneMatch(row -> "500".equals(row.getJibun()));
    }
}
