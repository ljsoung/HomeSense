package com.jiseong.homesense.batch.loader;

import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import org.testcontainers.containers.MariaDBContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import com.jiseong.homesense.batch.parser.dto.TradeDraft;
import com.jiseong.homesense.trade.entity.DealCategory;
import com.jiseong.homesense.trade.entity.HousingType;
import com.jiseong.homesense.trade.entity.Trade;
import com.jiseong.homesense.trade.repository.TradeRepository;

/**
 * TradeChunkLoaderTest의 "재시도" 테스트는 saveAndFlush()를 목킹해 DataIntegrityViolationException을
 * 강제로 던지는 순수 단위 테스트라 "재시도 코드가 실행된다"만 증명하고 "실제 DB against 재시도가
 * 성공적으로 커밋된다"는 증명하지 못한다. 이 테스트는 실제 MariaDB(Testcontainers) 위에서 두 트랜잭션이
 * 진짜로 같은 dedup_hash를 놓고 경쟁하게 만들어, TradeChunkLoader.upsertOne()의 재시도 경로가 실제
 * UNIQUE 제약까지 포함해 끝까지 커밋되는지 검증한다. 특히 TradeInsertGateway가 INSERT 시도를 별도
 * REQUIRES_NEW 트랜잭션으로 격리하지 못했다면, dedup_hash 충돌 시 청크 트랜잭션의 EntityManager가
 * rollback-only로 표시되어(JPA 스펙) 재시도 UPDATE가 겉보기엔 성공한 것처럼 보여도 커밋 시점에 청크
 * 전체가 롤백된다 — 이 테스트는 loadChunk() 이후 실제로 커밋된 행을 별도로 재조회해 이 실패 모드가
 * 재발하지 않는지까지 확인한다.
 *
 * <p>스키마는 테이블정의서 8장 원문 전체가 아니라 이 테스트가 실제로 건드리는 최소 부분집합을
 * 재구성한 것이다(testcontainers/trade-race-schema.sql) — 권위 있는 DDL은 여전히 테이블정의서 8장이다.
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
    @Autowired
    private PlatformTransactionManager transactionManager;

    private static TradeDraft draft(LocalDate registrationDate, String aptDong) {
        return new TradeDraft(
                HousingType.APT, DealCategory.SALE, null, "15126468", "11680", "역삼동", "역삼래미안",
                "123-4", new BigDecimal("84.99"), (short) 10, (short) 2005, LocalDate.of(2024, 1, 15),
                120000L, null, null, aptDong, "AGENT", "강남구", registrationDate, null, null, null,
                false, null, null, null, null, null);
    }

    /**
     * dealDate/dealAmount가 달라 winnerDraft/loserDraft와 절대 같은 dedup_hash를 만들지 않는 별개 건.
     */
    private static TradeDraft unrelatedDraft() {
        return new TradeDraft(
                HousingType.APT, DealCategory.SALE, null, "15126468", "11680", "역삼동", "역삼래미안",
                "123-4", new BigDecimal("84.99"), (short) 10, (short) 2005, LocalDate.of(2024, 2, 1),
                130000L, null, null, "103동", "AGENT", "강남구", LocalDate.of(2024, 2, 1), null, null, null,
                false, null, null, null, null, null);
    }

    @Test
    void 두_트랜잭션이_같은_dedup_hash로_동시에_INSERT를_시도하면_하나는_재시도로_UPDATE에_성공하고_같은_청크의_다른_건도_함께_커밋된다()
            throws Exception {
        TradeDraft winnerDraft = draft(LocalDate.of(2024, 1, 20), "101동");
        TradeDraft loserDraft = draft(LocalDate.of(2024, 1, 25), "102동");
        // dedup_hash 충돌과 무관한, 같은 청크에 함께 들어가는 정상 건 — TradeInsertGateway의 REQUIRES_NEW
        // 격리가 없다면 loserDraft의 충돌로 청크 트랜잭션 전체가 rollback-only로 표시되어 이 건까지
        // 함께 유실된다(코드리뷰에서 지적된 "최대 500건 전체 유실" 시나리오를 그대로 재현).
        TradeDraft unrelatedDraft = unrelatedDraft();
        String expectedHash = dedupHashCalculator.calculate(winnerDraft);
        // aptDong/registrationDate만 다르고 나머지 필드(complexId 포함, 둘 다 null)는 같으므로
        // 두 draft는 반드시 같은 dedup_hash를 만든다.
        assertThat(dedupHashCalculator.calculate(loserDraft)).isEqualTo(expectedHash);
        assertThat(dedupHashCalculator.calculate(unrelatedDraft)).isNotEqualTo(expectedHash);

        CountDownLatch insertedLatch = new CountDownLatch(1);
        CountDownLatch releaseLatch = new CountDownLatch(1);
        TransactionTemplate transactionTemplate = new TransactionTemplate(transactionManager);

        ExecutorService executor = Executors.newFixedThreadPool(2);
        try {
            // Thread A: findByDedupHash(miss) -> INSERT까지만 실행하고, releaseLatch가 열릴 때까지
            // 커밋하지 않고 트랜잭션을 붙잡아 둔다 — "다른 배치 실행이 먼저 INSERT를 끝낸 동시성
            // race condition"에서 "먼저 INSERT한 쪽"을 실제 트랜잭션으로 재현한다.
            Future<?> holderFuture = executor.submit(() -> transactionTemplate.executeWithoutResult(status -> {
                assertThat(tradeRepository.findByDedupHash(expectedHash)).isEmpty();
                Trade newTrade = Trade.builder()
                        .housingType(winnerDraft.housingType())
                        .dealCategory(winnerDraft.dealCategory())
                        .datasetId(winnerDraft.datasetId())
                        .sggCd(winnerDraft.sggCd())
                        .excluUseArea(winnerDraft.excluUseArea())
                        .floor(winnerDraft.floor())
                        .dealDate(winnerDraft.dealDate())
                        .dealAmount(winnerDraft.dealAmount())
                        .aptDong(winnerDraft.aptDong())
                        .registrationDate(winnerDraft.registrationDate())
                        .cancelYn(winnerDraft.cancelYn())
                        .dedupHash(expectedHash)
                        .build();
                tradeRepository.saveAndFlush(newTrade);
                insertedLatch.countDown();
                awaitUninterruptibly(releaseLatch);
            }));

            // A가 INSERT까지는 마쳤지만 아직 커밋 전이라는 것을 확인한 뒤, 같은 dedup_hash를 노리는
            // loserDraft를 실제 프로덕션 경로(TradeChunkLoader.loadChunk)로 적재한다. REPEATABLE READ라
            // B의 findByDedupHash는 A의 미확정 INSERT를 보지 못해 빈 결과를 받고, B도 INSERT를 시도하다
            // A가 쥔 미확정 UNIQUE 인덱스 항목에 걸려 블록된다.
            assertThat(insertedLatch.await(10, TimeUnit.SECONDS)).isTrue();
            Future<ChunkOutcome> loserFuture = executor.submit(
                    () -> tradeChunkLoader.loadChunk(List.of(loserDraft, unrelatedDraft)));

            // B가 findByDedupHash를 지나 블로킹 INSERT에 도달할 시간을 준 뒤 A를 풀어 커밋시킨다 —
            // A가 커밋되는 순간 B의 블록된 INSERT가 재개되며 실제 dedup_hash UNIQUE 위반으로 실패하고,
            // TradeChunkLoader의 재시도(UPDATE) 경로를 탄다. loserDraft를 청크의 첫 번째 건으로 넣어
            // 충돌을 겪게 하고, 뒤이어 같은 청크 트랜잭션에서 처리되는 unrelatedDraft가 정상적으로
            // 커밋되는지까지 확인한다.
            Thread.sleep(500);
            releaseLatch.countDown();
            holderFuture.get(10, TimeUnit.SECONDS);

            ChunkOutcome outcome = loserFuture.get(10, TimeUnit.SECONDS);

            assertThat(outcome.result()).isEqualTo(new LoadResult(2, 0, 1, 1));

            List<Trade> rows = tradeRepository.findAll();
            assertThat(rows).hasSize(2);
            Trade retried = rows.stream()
                    .filter(row -> row.getDedupHash().equals(expectedHash))
                    .findFirst()
                    .orElseThrow();
            // 재시도(UPDATE)가 실제로 커밋됐다는 증거 — loserDraft의 값으로 덮였어야 한다.
            assertThat(retried.getAptDong()).isEqualTo("102동");
            assertThat(retried.getRegistrationDate()).isEqualTo(LocalDate.of(2024, 1, 25));
            // 청크의 나머지 건(unrelatedDraft)도 함께 커밋됐다는 증거 — REQUIRES_NEW 격리가 없었다면
            // loserDraft의 충돌로 이 건까지 롤백됐을 것이다.
            assertThat(rows).anySatisfy(row -> assertThat(row.getAptDong()).isEqualTo("103동"));
        } finally {
            executor.shutdownNow();
        }
    }

    private void awaitUninterruptibly(CountDownLatch latch) {
        boolean interrupted = false;
        try {
            while (true) {
                try {
                    latch.await();
                    return;
                } catch (InterruptedException e) {
                    interrupted = true;
                }
            }
        } finally {
            if (interrupted) {
                Thread.currentThread().interrupt();
            }
        }
    }
}
