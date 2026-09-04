package com.jiseong.homesense.common.cache;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.File;
import java.io.IOException;
import java.nio.charset.Charset;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.cache.Cache;
import org.springframework.cache.CacheManager;
import org.springframework.cache.concurrent.ConcurrentMapCacheManager;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import org.testcontainers.containers.MariaDBContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import com.jiseong.homesense.batch.matcher.LegalDistrictCodeLoader;

/**
 * CacheEvictionListener.onLegalDistrictCodeReloaded()를 {@code @EventListener}에서
 * {@code @TransactionalEventListener(AFTER_COMMIT)}로 바꾼 것은 코드리뷰(PR #14)에서 지적된 실제
 * 버그를 고친 것이다 — LegalDistrictCodeLoader.loadInitial()이 {@code @Transactional} 안에서
 * 이벤트를 발행하는데 일반 리스너는 커밋 전에 동기 실행되어, evict~커밋 사이의 창에서 다른 트랜잭션이
 * 구버전 행을 읽어 캐시를 재적재하면 evict가 무의미해질 수 있었다. 이 프로젝트에서 실제로 문제가
 * 재현됐던 지점이 여기뿐이라, 단위 테스트(CacheEvictionListenerTest)처럼 리스너 메서드를 직접
 * 호출해 "무엇을 evict하는지"만 확인하는 걸로는 부족하다 — "커밋 전에는 절대 실행되지 않는다"는
 * 시점 자체를 실제 트랜잭션 커밋 경계로 증명해야 회귀를 잡을 수 있다.
 *
 * <p>TradeChunkLoaderMariaDbIT와 같은 패턴(Testcontainers MariaDB + CountDownLatch로 트랜잭션을
 * 붙잡아 둔 채 커밋 전/후 상태를 관찰)을 쓰되, Redis는 이 프로젝트에 Testcontainers 설정이 없어
 * CacheConfig의 RedisCacheManager 대신 인메모리 ConcurrentMapCacheManager를 {@code @Primary}로
 * 주입한다 — 검증 대상은 "Redis 캐시가 실제로 지워지는지"가 아니라 "AFTER_COMMIT 타이밍이 지켜지는지"
 * (Spring 트랜잭션 동기화 메커니즘 자체)이므로 실제 캐시 저장소 종류는 무관하다.
 *
 * <p>스키마는 TradeChunkLoaderMariaDbIT가 이미 쓰는 최소 재구성 스키마(trade-race-schema.sql)를
 * 그대로 재사용한다 — 이 테스트가 실제로 건드리는 건 legal_district_code 테이블뿐이다.
 *
 * <p>Docker가 필요해 기본 `./gradlew test`에서는 제외되고 `./gradlew integrationTest`로만 실행된다.
 */
@SpringBootTest
@Testcontainers
@Tag("integration")
class CacheEvictionListenerMariaDbIT {

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

    @TestConfiguration
    static class InMemoryCacheManagerConfig {
        @Bean
        @Primary
        CacheManager testCacheManager() {
            return new ConcurrentMapCacheManager("regionAutocomplete");
        }
    }

    @Autowired
    private LegalDistrictCodeLoader legalDistrictCodeLoader;
    @Autowired
    private CacheManager cacheManager;
    @Autowired
    private PlatformTransactionManager transactionManager;

    @TempDir
    Path tempDir;

    @Test
    void 법정동코드_재적재_트랜잭션이_커밋되기_전에는_regionAutocomplete가_evict되지_않고_커밋된_후에만_evict된다()
            throws Exception {
        Cache regionAutocomplete = cacheManager.getCache("regionAutocomplete");
        regionAutocomplete.put("강남", List.of("강남구"));
        assertThat(regionAutocomplete.get("강남")).isNotNull();

        File csvFile = writeMinimalCsv();

        CountDownLatch loadedLatch = new CountDownLatch(1);
        CountDownLatch releaseLatch = new CountDownLatch(1);
        TransactionTemplate transactionTemplate = new TransactionTemplate(transactionManager);

        ExecutorService executor = Executors.newSingleThreadExecutor();
        try {
            // loadInitial()은 @Transactional이지만 propagation REQUIRED라, 이미 열려 있는(여기서
            // TransactionTemplate이 시작한) 트랜잭션에 그대로 합류한다 — 즉 이 콜백 전체가 물리적으로
            // 하나의 트랜잭션이고, releaseLatch로 콜백을 붙잡아 두는 동안은 loadInitial() 내부에서
            // 발행한 이벤트가 아직 커밋되지 않은 상태로 남는다.
            Future<?> future = executor.submit(() -> transactionTemplate.executeWithoutResult(status -> {
                legalDistrictCodeLoader.loadInitial(csvFile);
                loadedLatch.countDown();
                awaitUninterruptibly(releaseLatch);
            }));

            assertThat(loadedLatch.await(10, TimeUnit.SECONDS)).isTrue();
            // loadInitial()은 이미 반환했지만(이벤트 발행 코드까지 실행 완료) 트랜잭션은 아직 커밋 전이다.
            // AFTER_COMMIT 리스너가 정상 동작한다면 이 시점엔 아직 evict가 일어나지 않아야 한다 —
            // 만약 예전처럼 일반 @EventListener였다면 이 assertion에서 이미 null이어야 정상이라 실패했을
            // 시나리오다.
            assertThat(regionAutocomplete.get("강남")).isNotNull();

            releaseLatch.countDown();
            future.get(10, TimeUnit.SECONDS);

            // 콜백이 반환되며 트랜잭션이 실제로 커밋된 뒤에는 AFTER_COMMIT 리스너가 실행돼 evict돼 있어야 한다.
            assertThat(regionAutocomplete.get("강남")).isNull();
        } finally {
            executor.shutdownNow();
        }
    }

    private File writeMinimalCsv() throws IOException {
        Path csvPath = tempDir.resolve("legal_district_code.csv");
        String content = "법정동코드,법정동명,폐지여부\n";
        Files.write(csvPath, content.getBytes(Charset.forName("MS949")));
        return csvPath.toFile();
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
