package com.jiseong.homesense.batch.notifier;

import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;
import java.sql.PreparedStatement;
import java.sql.Statement;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.domain.PageRequest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.support.GeneratedKeyHolder;
import org.springframework.jdbc.support.KeyHolder;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.MariaDBContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import com.jiseong.homesense.batch.notifier.NotificationTriggerResult.SkipReason;
import com.jiseong.homesense.notification.dto.NotificationResponse;
import com.jiseong.homesense.notification.service.NotificationService;
import com.jiseong.homesense.user.service.WithdrawalTestSeed;

/**
 * BAT-NTF-01 — 실제 MariaDB 위에서 평가 쿼리(설정 ⨝ 관심 매물/지역 ⨝ 회원, created_at 기준 신규 거래, 기준 평균
 * GROUP BY, 지역 prefix LIKE)와 알림 INSERT가 의도대로 동작하는지 검증한다. 쿼리의 WHERE 절이 맞는 행을 고르는지는
 * Mockito로 증명할 수 없다.
 *
 * <p>픽스처(서울 종로구, 런 시작 2026-10-06 03:00:00):
 * <ul>
 *   <li>단지 C1(숭인동): 기준 매매 3건(3.3㎡당 약 3,306만원, 런 전 적재), 신규 매매 2건(약 3,636만원, +10.0%),
 *       신규 전세 1건(가장 최근 계약 → 대표 거래), 신규 해제 매매 1건(제외)</li>
 *   <li>창신동(단지 없음): 기준 매매 2건(표본 부족), 신규 매매 1건</li>
 *   <li>ACTIVE 회원 A: C1 관심 매물, 창신동(동) 관심 지역, 종로구(시군구) 관심 지역. WITHDRAWN 회원 W: C1 관심 매물</li>
 * </ul>
 *
 * <p>DDL은 {@code testcontainers/withdrawn-user-purge-schema.sql}(schema_all.sql에서 추출한 원문, sent_at NULL 허용).
 * Docker가 필요해 {@code ./gradlew integrationTest}로만 실행된다.
 */
@SpringBootTest
@Testcontainers
@Tag("integration")
class WatchConditionEvaluatorMariaDbIT {

    private static final LocalDateTime RUN = LocalDateTime.of(2026, 10, 6, 3, 0, 0);
    private static final LocalDate RUN_DATE = LocalDate.of(2026, 10, 6);
    private static final LocalDateTime BEFORE_RUN = RUN.minusDays(1);
    private static final LocalDateTime AFTER_RUN = RUN.plusMinutes(5);

    private static final String SIDO = "1100000000";
    private static final String JONGNO = "1111000000";
    private static final String SUNGIN = "1111017400";
    private static final String CHANGSIN = "1111017500";

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
        // 03:00 수집 스케줄이 테스트 도중 끼어들지 않게 한다
        registry.add("homesense.scheduling.enabled", () -> "false");
    }

    @Autowired
    private JdbcTemplate jdbc;
    @Autowired
    private WatchConditionEvaluator evaluator;
    @Autowired
    private NotificationService notificationService;

    private WithdrawalTestSeed seed;
    private long userA;
    private long userW;
    private long complex1;
    private long jeonseTradeId;
    private long changsinSaleId;

    @BeforeEach
    void setUp() {
        seed = new WithdrawalTestSeed(jdbc);
        seed.wipeAll();
        // 법정동 prefix 맵(RegionCodePrefixResolver)은 JVM에 남으므로 테스트마다 같은 코드를 넣는다
        legalDistrict(SIDO, "서울특별시", "서울특별시", null, null);
        legalDistrict(JONGNO, "서울특별시 종로구", "서울특별시", "종로구", null);
        legalDistrict(SUNGIN, "서울특별시 종로구 숭인동", "서울특별시", "종로구", "숭인동");
        legalDistrict(CHANGSIN, "서울특별시 종로구 창신동", "서울특별시", "종로구", "창신동");

        userA = seed.user("a@test.com", "pw", "ACTIVE", null);
        userW = seed.user("w@test.com", "pw", "WITHDRAWN", RUN.minusDays(2));
        complex1 = seed.complex("C1");
        jdbc.update("UPDATE complex SET complex_name = '숭인 힐스테이트' WHERE complex_id = ?", complex1);

        // 기준(런 전 적재, 직전 3개월): C1 3건, 창신동 2건 — 3.3㎡당 100,000 × 3.3058 / 100 ≈ 3,305.8만원
        sale(complex1, SUNGIN, "2026-08-10", 100_000L, BEFORE_RUN, false);
        sale(complex1, SUNGIN, "2026-09-01", 100_000L, BEFORE_RUN, false);
        sale(complex1, SUNGIN, "2026-09-20", 100_000L, BEFORE_RUN, false);
        sale(null, CHANGSIN, "2026-08-15", 100_000L, BEFORE_RUN, false);
        sale(null, CHANGSIN, "2026-09-15", 100_000L, BEFORE_RUN, false);
        // 기간 밖(3개월 이전) — 기준에 들지 않는다
        sale(complex1, SUNGIN, "2026-06-01", 10_000L, BEFORE_RUN, false);

        // 이번 런 신규
        sale(complex1, SUNGIN, "2026-10-01", 110_000L, AFTER_RUN, false);
        sale(complex1, SUNGIN, "2026-10-02", 110_000L, AFTER_RUN, false);
        jeonseTradeId = trade(complex1, SUNGIN, "RENT", "JEONSE", null, 50_000L, 0L, "2026-10-03", AFTER_RUN, false);
        sale(complex1, SUNGIN, "2026-10-05", 999_999L, AFTER_RUN, true); // 해제 — 제외
        changsinSaleId = sale(null, CHANGSIN, "2026-10-02", 100_000L, AFTER_RUN, false);

        long favA = seed.favoriteProperty(userA, complex1);
        setting("favorite_property_id", userA, favA, "5.0");
        long favW = seed.favoriteProperty(userW, complex1);
        setting("favorite_property_id", userW, favW, "0.0");
        setting("favorite_region_id", userA, seed.favoriteRegion(userA, CHANGSIN), "5.0");
        setting("favorite_region_id", userA, seed.favoriteRegion(userA, JONGNO), "5.0");
    }

    private NotificationTriggerContext context(LocalDateTime runStartedAt) {
        return new NotificationTriggerContext(runStartedAt, RUN_DATE, Set.of(complex1), Set.of(SUNGIN, CHANGSIN));
    }

    @Test
    void 기대한_알림만_만든다() {
        NotificationTriggerResult result = evaluator.evaluateAfterLoad(context(RUN));

        // 평가 대상: A의 매물·창신동·종로구 3건(W는 WITHDRAWN이라 조회되지 않음)
        assertThat(result.evaluatedSettings()).isEqualTo(3);
        assertThat(result.newTradeCreated()).isEqualTo(3);
        assertThat(result.priceChangeCreated()).isEqualTo(2);
        assertThat(result.skippedCount(SkipReason.BASELINE_TOO_SMALL)).isEqualTo(1); // 창신동
        assertThat(result.failed()).isZero();

        List<Map<String, Object>> rows = jdbc.queryForList(
                "SELECT * FROM notification ORDER BY notification_id");
        assertThat(rows).hasSize(5)
                .allSatisfy(row -> {
                    assertThat(((Number) row.get("user_id")).longValue()).isEqualTo(userA);
                    assertThat(row.get("sent_at")).isNull();
                    assertThat(row.get("is_read")).isIn(false, 0, (byte) 0);
                });

        Map<String, Object> propertyNew = find(rows, "NEW_TRADE", complex1, null);
        assertThat(propertyNew.get("title")).isEqualTo("숭인 힐스테이트 신규 실거래 3건");
        assertThat(((Number) propertyNew.get("trade_id")).longValue()).isEqualTo(jeonseTradeId);
        assertThat(propertyNew.get("legal_dong_cd")).isNull();
        assertThat((String) propertyNew.get("message")).startsWith("2026.10.03 계약").endsWith("전세 5억 외 2건");

        Map<String, Object> propertyPrice = find(rows, "PRICE_CHANGE", complex1, null);
        assertThat(propertyPrice.get("title")).isEqualTo("숭인 힐스테이트 실거래가 10.0% 상승");
        assertThat(propertyPrice.get("trade_id")).isNull();
        assertThat((String) propertyPrice.get("message")).contains("신규 매매 2건");

        Map<String, Object> changsinNew = find(rows, "NEW_TRADE", null, CHANGSIN);
        assertThat(changsinNew.get("title")).isEqualTo("서울특별시 종로구 창신동 신규 실거래 1건");
        assertThat(((Number) changsinNew.get("trade_id")).longValue()).isEqualTo(changsinSaleId);

        // 시군구 관심 지역: 숭인동 신규 3건 + 창신동 1건, 기준 5건 대비 +6.7%
        Map<String, Object> jongnoNew = find(rows, "NEW_TRADE", null, JONGNO);
        assertThat(jongnoNew.get("title")).isEqualTo("서울특별시 종로구 신규 실거래 4건");
        assertThat(((Number) jongnoNew.get("trade_id")).longValue()).isEqualTo(jeonseTradeId);
        Map<String, Object> jongnoPrice = find(rows, "PRICE_CHANGE", null, JONGNO);
        assertThat(jongnoPrice.get("title")).isEqualTo("서울특별시 종로구 실거래가 6.7% 상승");
        assertThat(jongnoPrice.get("complex_id")).isNull();
    }

    @Test
    void 같은_거래로_다시_돌려도_새_런에서는_알림이_생기지_않는다() {
        evaluator.evaluateAfterLoad(context(RUN));
        long before = seed.count("SELECT COUNT(*) FROM notification");

        NotificationTriggerResult rerun = evaluator.evaluateAfterLoad(context(RUN.plusDays(1)));

        assertThat(rerun.newTradeCreated() + rerun.priceChangeCreated()).isZero();
        assertThat(seed.count("SELECT COUNT(*) FROM notification")).isEqualTo(before);
    }

    @Test
    void 임계치보다_작은_변동은_알리지_않는다() {
        jdbc.update("UPDATE notification_setting SET price_change_threshold_pct = 10.1 WHERE user_id = ?", userA);

        NotificationTriggerResult result = evaluator.evaluateAfterLoad(context(RUN));

        assertThat(result.priceChangeCreated()).isZero();
        assertThat(result.skippedCount(SkipReason.BELOW_THRESHOLD)).isEqualTo(2);
        assertThat(result.newTradeCreated()).isEqualTo(3);
    }

    @Test
    void 신규거래_알림을_끄면_NEW_TRADE를_만들지_않는다() {
        jdbc.update("UPDATE notification_setting SET new_trade_alert_yn = FALSE WHERE user_id = ?", userA);

        NotificationTriggerResult result = evaluator.evaluateAfterLoad(context(RUN));

        assertThat(result.newTradeCreated()).isZero();
        assertThat(result.skippedCount(SkipReason.NEW_TRADE_ALERT_OFF)).isEqualTo(3);
        assertThat(result.priceChangeCreated()).isEqualTo(2);
    }

    @Test
    void 영향_집합이_비면_아무것도_조회하지_않고_끝낸다() {
        NotificationTriggerResult result = evaluator.evaluateAfterLoad(
                new NotificationTriggerContext(RUN, RUN_DATE, Set.of(), Set.of()));

        assertThat(result.evaluatedSettings()).isZero();
        assertThat(seed.count("SELECT COUNT(*) FROM notification")).isZero();
    }

    @Test
    void 미발송_알림도_목록에_발생_시각_최신순으로_보인다() {
        evaluator.evaluateAfterLoad(context(RUN));

        List<NotificationResponse> page = notificationService.getNotifications(userA, null, PageRequest.of(0, 20))
                .getContent();

        assertThat(page).hasSize(5).allSatisfy(n -> {
            assertThat(n.sentAt()).isNull();
            assertThat(n.createdAt()).isNotNull();
        });
        // 같은 초에 만들어지므로 ID 역순으로 정렬된다
        assertThat(page).extracting(NotificationResponse::notificationId)
                .isSortedAccordingTo((x, y) -> Long.compare(y, x));
    }

    private Map<String, Object> find(List<Map<String, Object>> rows, String type, Long complexId, String legalDongCd) {
        return rows.stream()
                .filter(r -> type.equals(r.get("notification_type")))
                .filter(r -> complexId == null ? r.get("complex_id") == null
                        : r.get("complex_id") != null && ((Number) r.get("complex_id")).longValue() == complexId)
                .filter(r -> legalDongCd == null ? r.get("legal_dong_cd") == null : legalDongCd.equals(r.get("legal_dong_cd")))
                .findFirst()
                .orElseThrow(() -> new AssertionError("알림 없음: " + type + " " + complexId + " " + legalDongCd));
    }

    private void legalDistrict(String code, String name, String sido, String sigungu, String eupmyeondong) {
        jdbc.update("INSERT INTO legal_district_code (legal_dong_cd, legal_dong_name, sido_name, sigungu_name, "
                + "eupmyeondong_name, is_active, data_version) VALUES (?, ?, ?, ?, ?, TRUE, '2026-09-01')",
                code, name, sido, sigungu, eupmyeondong);
    }

    private void setting(String targetColumn, long userId, long favoriteId, String threshold) {
        jdbc.update("INSERT INTO notification_setting (user_id, " + targetColumn + ", price_change_threshold_pct, "
                + "new_trade_alert_yn, email_alert_yn, created_at, updated_at) VALUES (?, ?, ?, TRUE, FALSE, ?, ?)",
                userId, favoriteId, new BigDecimal(threshold), BEFORE_RUN, BEFORE_RUN);
    }

    private long sale(Long complexId, String legalDongCd, String dealDate, long amount, LocalDateTime createdAt,
                      boolean cancelled) {
        return trade(complexId, legalDongCd, "SALE", null, amount, null, null, dealDate, createdAt, cancelled);
    }

    private long trade(Long complexId, String legalDongCd, String category, String rentType, Long dealAmount,
                       Long deposit, Long monthly, String dealDate, LocalDateTime createdAt, boolean cancelled) {
        String hash = Long.toHexString(System.nanoTime()) + Long.toHexString(Double.doubleToLongBits(Math.random()));
        KeyHolder keys = new GeneratedKeyHolder();
        Object[] args = {category, rentType, LocalDate.parse(dealDate), dealAmount, deposit, monthly, cancelled, hash,
                complexId, legalDongCd, createdAt, createdAt};
        String sql = "INSERT INTO trade (housing_type, deal_category, rent_type, dataset_id, sgg_cd, exclu_use_area, "
                + "floor, deal_date, deal_amount, deposit_amount, monthly_rent_amount, cancel_yn, dedup_hash, complex_id, "
                + "legal_dong_cd, created_at, updated_at) "
                + "VALUES ('APT', ?, ?, '15126469', '11110', 100.00, 7, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)";
        jdbc.update(connection -> {
            PreparedStatement ps = connection.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS);
            for (int i = 0; i < args.length; i++) {
                ps.setObject(i + 1, args[i]);
            }
            return ps;
        }, keys);
        return keys.getKey().longValue();
    }
}
