package com.jiseong.homesense.user.service;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Clock;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.Map;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import org.testcontainers.containers.MariaDBContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import com.jiseong.homesense.batch.scheduler.WithdrawnUserPurgeScheduler;
import com.jiseong.homesense.user.repository.UserRepository;

/**
 * BAT-USR-01 — 실제 MariaDB 위에서 "조건부 DELETE 1문 + DB CASCADE" 파기가 의도대로 동작하는지 검증한다. 이 검증은
 * Mockito로 증명할 수 없다: 자식 테이블이 진짜로 지워지는지(FK ON DELETE CASCADE 방향), 부모(complex/
 * legal_district_code/trade)가 그대로 남는지, 경계값이 DB의 DATETIME 비교에서 정확히 나뉘는지, 파기와 철회의
 * 조건부 문이 같은 행에서 서로 배타적인지는 실 DB의 affected rows로만 확인된다.
 *
 * <p>DDL은 {@code testcontainers/withdrawn-user-purge-schema.sql}(schema_all.sql에서 추출한 원문)이다. 시간은
 * 고정 KST Clock이라 "정확히 N일"·"1초 덜"을 결정적으로 만든다. 스케줄러는 빈 자동 등록 대신 직접 생성해
 * 실제 05:00 cron이 테스트 도중에 끼어들 여지를 없앤다.
 *
 * <p>Docker가 필요해 기본 {@code ./gradlew test}에서는 제외되고 {@code ./gradlew integrationTest}로만 실행된다.
 */
@SpringBootTest
@Testcontainers
@Tag("integration")
@ExtendWith(OutputCaptureExtension.class)
class WithdrawnUserPurgeMariaDbIT {

    private static final ZoneId KST = ZoneId.of("Asia/Seoul");
    private static final LocalDateTime NOW = LocalDateTime.of(2026, 9, 21, 5, 0, 0);
    private static final LocalDateTime THRESHOLD = NOW.minusDays(7);

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
        registry.add("spring.sql.init.schema-locations", () -> "classpath:testcontainers/withdrawn-user-purge-schema.sql");
    }

    @TestConfiguration
    static class FixedClockConfig {

        @Bean
        @Primary
        Clock fixedClock() {
            return Clock.fixed(NOW.atZone(KST).toInstant(), KST);
        }
    }

    @Autowired
    private JdbcTemplate jdbc;
    @Autowired
    private WithdrawnUserPurgeService purgeService;
    @Autowired
    private WithdrawalPolicy withdrawalPolicy;
    @Autowired
    private UserRepository userRepository;
    @Autowired
    private PlatformTransactionManager transactionManager;

    private WithdrawalTestSeed seed;
    private WithdrawnUserPurgeScheduler scheduler;

    @BeforeEach
    void setUp() {
        seed = new WithdrawalTestSeed(jdbc);
        seed.wipeAll();
        scheduler = new WithdrawnUserPurgeScheduler(purgeService, withdrawalPolicy, Clock.fixed(NOW.atZone(KST).toInstant(), KST));
    }

    private int inTx(java.util.function.IntSupplier operation) {
        Integer result = new TransactionTemplate(transactionManager).execute(status -> operation.getAsInt());
        return result == null ? -1 : result;
    }

    @Test
    void 정확히_N일_경과는_파기되고_1초라도_덜_지났으면_유지된다() {
        long exactlyN = seed.user("exact@test.com", "pw", "WITHDRAWN", THRESHOLD);
        long oneSecondShort = seed.user("short@test.com", "pw", "WITHDRAWN", THRESHOLD.plusSeconds(1));
        long oneSecondOver = seed.user("over@test.com", "pw", "WITHDRAWN", THRESHOLD.minusSeconds(1));

        scheduler.runDailyPurge();

        assertThat(seed.userExists(exactlyN)).as("정확히 N일 경과 → 파기").isFalse();
        assertThat(seed.userExists(oneSecondOver)).as("N일 + 1초 경과 → 파기").isFalse();
        assertThat(seed.userExists(oneSecondShort)).as("N일보다 1초 덜 경과 → 유지").isTrue();
    }

    @Test
    void 오래된_ACTIVE와_SUSPENDED_계정은_파기하지_않는다() {
        long active = seed.user("active@test.com", "pw", "ACTIVE", null);
        long suspended = seed.user("suspended@test.com", "pw", "SUSPENDED", null);
        // 비정상 데이터 방어: ACTIVE인데 오래된 withdrawn_at이 남아 있어도 status 조건 때문에 지워지면 안 된다.
        long activeWithStaleTimestamp = seed.user("stale@test.com", "pw", "ACTIVE", NOW.minusDays(30));

        scheduler.runDailyPurge();

        assertThat(seed.userExists(active)).isTrue();
        assertThat(seed.userExists(suspended)).isTrue();
        assertThat(seed.userExists(activeWithStaleTimestamp)).isTrue();
    }

    @Test
    void withdrawn_at이_없는_WITHDRAWN_행은_삭제하지_않고_WARN으로_건수를_남긴다(CapturedOutput output) {
        long malformed = seed.user("malformed@test.com", "pw", "WITHDRAWN", null);
        long expired = seed.user("expired@test.com", "pw", "WITHDRAWN", NOW.minusDays(30));

        scheduler.runDailyPurge();

        assertThat(seed.userExists(malformed)).as("탈퇴 시각을 알 수 없는 행은 절대 삭제하지 않는다").isTrue();
        assertThat(seed.userExists(expired)).isFalse();
        assertThat(output.getAll())
                .contains("WITHDRAWN_USER_PURGE skipped rows with status=WITHDRAWN and withdrawn_at IS NULL count=1")
                .contains("purged=1 skipped=0 failed=0 malformedWithdrawn=1");
    }

    @Test
    void 파기하면_모든_자식_테이블이_CASCADE로_삭제되고_부모_테이블과_다른_회원의_데이터는_그대로_남는다() {
        seed.legalDistrict("1111010100");
        long complexId = seed.complex("C-1");
        long tradeId = seed.trade(complexId, "1111010100", "a".repeat(64));

        long purged = seed.user("purge@test.com", "pw", "WITHDRAWN", NOW.minusDays(8));
        long kept = seed.user("kept@test.com", "pw", "ACTIVE", null);
        seed.seedAllChildren(purged, complexId, "1111010100", tradeId, "token-purged");
        seed.seedAllChildren(kept, complexId, "1111010100", tradeId, "token-kept");
        Map<String, Long> keptBefore = seed.childCounts(kept);
        assertThat(seed.childCounts(purged)).allSatisfy((table, count) -> assertThat(count).as(table).isEqualTo(
                table.equals("notification_setting") ? 2L : 1L));

        scheduler.runDailyPurge();

        assertThat(seed.userExists(purged)).isFalse();
        // user를 참조하는 6개 테이블(설정→관심 매물/지역 체인 포함)이 전부 비어야 한다.
        assertThat(seed.childCounts(purged)).allSatisfy((table, count) -> assertThat(count).as(table).isZero());
        // 부모는 그대로 — CASCADE 방향이 반대로 걸려 있으면 여기서 깨진다.
        assertThat(seed.count("SELECT COUNT(*) FROM complex")).isEqualTo(1);
        assertThat(seed.count("SELECT COUNT(*) FROM legal_district_code")).isEqualTo(1);
        assertThat(seed.count("SELECT COUNT(*) FROM trade")).isEqualTo(1);
        // 살아남는 회원의 데이터는 하나도 줄지 않는다.
        assertThat(seed.userExists(kept)).isTrue();
        assertThat(seed.childCounts(kept)).isEqualTo(keptBefore);
    }

    @Test
    void 재실행해도_멱등이고_이미_파기된_계정은_다시_세지_않는다(CapturedOutput output) {
        seed.user("purge@test.com", "pw", "WITHDRAWN", NOW.minusDays(8));
        long kept = seed.user("kept@test.com", "pw", "ACTIVE", null);

        scheduler.runDailyPurge();
        scheduler.runDailyPurge();

        assertThat(seed.userExists(kept)).isTrue();
        assertThat(seed.count("SELECT COUNT(*) FROM `user`")).isEqualTo(1);
        assertThat(output.getAll())
                .contains("purged=1 skipped=0 failed=0") // 1차 실행
                .contains("purged=0 skipped=0 failed=0"); // 2차 실행: 대상 자체가 없다
    }

    @Test
    void 청크_크기보다_많은_대상도_전부_파기한다() {
        int total = WithdrawnUserPurgeScheduler.CHUNK_SIZE + 25;
        for (int i = 0; i < total; i++) {
            seed.user("bulk" + i + "@test.com", "pw", "WITHDRAWN", NOW.minusDays(9));
        }
        long kept = seed.user("kept@test.com", "pw", "ACTIVE", null);

        scheduler.runDailyPurge();

        assertThat(seed.count("SELECT COUNT(*) FROM `user` WHERE status = 'WITHDRAWN'")).isZero();
        assertThat(seed.userExists(kept)).isTrue();
    }

    @Test
    void 파기와_철회의_조건부_문은_경계에서_서로_배타적이다() {
        LocalDateTime threshold = withdrawalPolicy.graceThreshold();
        assertThat(threshold).isEqualTo(THRESHOLD);

        // 1) 정확히 N일 경과: 파기 대상이고 철회는 불가.
        long atBoundary = seed.user("boundary@test.com", "pw", "WITHDRAWN", THRESHOLD);
        assertThat(inTx(() -> userRepository.reactivateIfWithinGrace(atBoundary, threshold, NOW))).as("철회").isZero();
        assertThat(inTx(() -> userRepository.deleteIfPurgeable(atBoundary, threshold))).as("파기").isEqualTo(1);
        assertThat(seed.userExists(atBoundary)).isFalse();

        // 2) 1초 덜 경과: 철회 가능이고 파기는 불가. 철회 뒤에는 ACTIVE라 이후 파기도 0건이다.
        long inGrace = seed.user("grace@test.com", "pw", "WITHDRAWN", THRESHOLD.plusSeconds(1));
        assertThat(inTx(() -> userRepository.deleteIfPurgeable(inGrace, threshold))).as("파기").isZero();
        assertThat(inTx(() -> userRepository.reactivateIfWithinGrace(inGrace, threshold, NOW))).as("철회").isEqualTo(1);
        assertThat(inTx(() -> userRepository.deleteIfPurgeable(inGrace, threshold))).as("철회 후 파기").isZero();
        assertThat(seed.userExists(inGrace)).isTrue();
        assertThat(seed.count("SELECT COUNT(*) FROM `user` WHERE user_id = ? AND status = 'ACTIVE' AND withdrawn_at IS NULL",
                inGrace)).isEqualTo(1);

        // 3) 파기가 먼저 끝난 행은 철회할 수 없다(존재하지 않으므로 0건) — reactivate()가 410으로 번역하는 경우.
        long purgedFirst = seed.user("purgedfirst@test.com", "pw", "WITHDRAWN", THRESHOLD.minusDays(1));
        assertThat(inTx(() -> userRepository.deleteIfPurgeable(purgedFirst, threshold))).isEqualTo(1);
        assertThat(inTx(() -> userRepository.reactivateIfWithinGrace(purgedFirst, threshold, NOW))).isZero();
    }

    @Test
    void 철회는_updated_at을_직접_갱신하고_refresh_token과_소유_데이터를_건드리지_않는다() {
        TransactionTemplate tx = new TransactionTemplate(transactionManager);
        seed.legalDistrict("1111010100");
        long complexId = seed.complex("C-1");
        long tradeId = seed.trade(complexId, "1111010100", "b".repeat(64));
        long userId = seed.user("grace@test.com", "pw", "WITHDRAWN", NOW.minusDays(2));
        seed.seedAllChildren(userId, complexId, "1111010100", tradeId, "token-grace");
        Map<String, Long> before = seed.childCounts(userId);

        tx.execute(s -> userRepository.reactivateIfWithinGrace(userId, withdrawalPolicy.graceThreshold(), NOW));

        // JPQL 벌크 UPDATE는 auditing을 우회하므로 updated_at을 직접 세팅했는지 확인한다.
        assertThat(jdbc.queryForObject("SELECT updated_at FROM `user` WHERE user_id = ?", LocalDateTime.class, userId))
                .isEqualTo(NOW);
        assertThat(seed.childCounts(userId)).isEqualTo(before);
    }
}
