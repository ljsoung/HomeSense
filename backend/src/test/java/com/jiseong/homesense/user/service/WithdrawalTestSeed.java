package com.jiseong.homesense.user.service;

import java.sql.PreparedStatement;
import java.sql.Statement;
import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.Map;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.support.GeneratedKeyHolder;
import org.springframework.jdbc.support.KeyHolder;

/**
 * 탈퇴 파기·철회 IT의 시드/조회 헬퍼. 엔티티 대신 raw SQL로 넣는다 — 임의의 {@code withdrawn_at}(경계값)과
 * status 조합을 만들어야 하고, 엔티티 생성 메서드(User.createUser)는 ACTIVE 계정만 만들기 때문이다. 컬럼 구성은
 * {@code testcontainers/withdrawn-user-purge-schema.sql}(schema_all.sql에서 추출한 DDL)과 맞춘다.
 */
public final class WithdrawalTestSeed {

    private static final LocalDateTime CREATED = LocalDateTime.of(2026, 1, 1, 0, 0, 0);

    private final JdbcTemplate jdbc;

    public WithdrawalTestSeed(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    /** FK 의존성 역순으로 전부 비운다 — 테스트 메서드 사이의 격리(클래스에 @Transactional을 걸 수 없는 IT용). */
    public void wipeAll() {
        for (String table : new String[] {"notification", "notification_setting", "recent_view", "favorite_region",
                "favorite_property", "refresh_token", "trade", "complex", "user", "legal_district_code"}) {
            jdbc.update("DELETE FROM `" + table + "`");
        }
    }

    public long user(String email, String encodedPassword, String status, LocalDateTime withdrawnAt) {
        return insert("INSERT INTO `user` (email, password, nickname, role, status, withdrawn_at, created_at, updated_at) "
                + "VALUES (?, ?, ?, 'USER', ?, ?, ?, ?)", email, encodedPassword, "닉네임", status, withdrawnAt, CREATED, CREATED);
    }

    public void legalDistrict(String legalDongCd) {
        jdbc.update("INSERT INTO legal_district_code (legal_dong_cd, legal_dong_name, is_active, data_version) "
                + "VALUES (?, ?, TRUE, '2026-09-01')", legalDongCd, "서울특별시 종로구 청운동");
    }

    public long complex(String sourceComplexCd) {
        return insert("INSERT INTO complex (source_complex_cd, complex_name, elevator_passenger_count, "
                + "elevator_cargo_count, elevator_combined_count, data_updated_at, created_at, updated_at) "
                + "VALUES (?, ?, 0, 0, 0, '2026-08-01', ?, ?)", sourceComplexCd, "테스트단지", CREATED, CREATED);
    }

    public long trade(long complexId, String legalDongCd, String dedupHash) {
        return insert("INSERT INTO trade (housing_type, deal_category, dataset_id, sgg_cd, exclu_use_area, deal_date, "
                + "cancel_yn, dedup_hash, complex_id, legal_dong_cd, created_at, updated_at) "
                + "VALUES ('APT', 'SALE', '15126469', '11110', 84.90, '2026-08-01', FALSE, ?, ?, ?, ?, ?)",
                dedupHash, complexId, legalDongCd, CREATED, CREATED);
    }

    public long refreshToken(long userId, String tokenHash, boolean revoked) {
        return insert("INSERT INTO refresh_token (user_id, token_value, expires_at, revoked_yn, created_at) "
                + "VALUES (?, ?, ?, ?, ?)", userId, tokenHash, LocalDateTime.of(2027, 1, 1, 0, 0, 0), revoked, CREATED);
    }

    public long favoriteProperty(long userId, long complexId) {
        return insert("INSERT INTO favorite_property (user_id, housing_type, complex_id, registered_at, created_at) "
                + "VALUES (?, 'APT', ?, ?, ?)", userId, complexId, CREATED, CREATED);
    }

    public long favoriteRegion(long userId, String legalDongCd) {
        return insert("INSERT INTO favorite_region (user_id, legal_dong_cd, registered_at, created_at) "
                + "VALUES (?, ?, ?, ?)", userId, legalDongCd, CREATED, CREATED);
    }

    public long recentView(long userId, long complexId) {
        return insert("INSERT INTO recent_view (user_id, housing_type, complex_id, viewed_at, created_at) "
                + "VALUES (?, 'APT', ?, ?, ?)", userId, complexId, CREATED, CREATED);
    }

    public long notificationSettingForProperty(long userId, long favoritePropertyId) {
        return insert("INSERT INTO notification_setting (user_id, favorite_property_id, price_change_threshold_pct, "
                + "new_trade_alert_yn, email_alert_yn, created_at, updated_at) VALUES (?, ?, 5.0, TRUE, TRUE, ?, ?)",
                userId, favoritePropertyId, CREATED, CREATED);
    }

    public long notificationSettingForRegion(long userId, long favoriteRegionId) {
        return insert("INSERT INTO notification_setting (user_id, favorite_region_id, price_change_threshold_pct, "
                + "new_trade_alert_yn, email_alert_yn, created_at, updated_at) VALUES (?, ?, 5.0, TRUE, TRUE, ?, ?)",
                userId, favoriteRegionId, CREATED, CREATED);
    }

    public long notification(long userId, long complexId, String legalDongCd, long tradeId) {
        return insert("INSERT INTO notification (user_id, notification_type, title, complex_id, legal_dong_cd, trade_id, "
                + "is_read, sent_at, created_at) VALUES (?, 'NEW_TRADE', '신규 거래', ?, ?, ?, FALSE, ?, ?)",
                userId, complexId, legalDongCd, tradeId, CREATED, CREATED);
    }

    /** 한 회원이 소유한 자식 행 수(테이블별). 파기 후 전부 0이어야 하고, 살아남는 회원은 시드한 수 그대로여야 한다. */
    public Map<String, Long> childCounts(long userId) {
        Map<String, Long> counts = new LinkedHashMap<>();
        for (String table : new String[] {"refresh_token", "favorite_property", "favorite_region", "recent_view",
                "notification_setting", "notification"}) {
            counts.put(table, count("SELECT COUNT(*) FROM `" + table + "` WHERE user_id = ?", userId));
        }
        return counts;
    }

    public long count(String sql, Object... args) {
        Long value = jdbc.queryForObject(sql, Long.class, args);
        return value == null ? 0L : value;
    }

    public boolean userExists(long userId) {
        return count("SELECT COUNT(*) FROM `user` WHERE user_id = ?", userId) == 1;
    }

    /** 이 회원이 소유한 모든 종류의 자식 행을 하나씩 시드한다(부모 complex/legal/trade는 호출자가 미리 만든다). */
    public void seedAllChildren(long userId, long complexId, String legalDongCd, long tradeId, String tokenHash) {
        refreshToken(userId, tokenHash, false);
        long favProp = favoriteProperty(userId, complexId);
        long favRegion = favoriteRegion(userId, legalDongCd);
        recentView(userId, complexId);
        notificationSettingForProperty(userId, favProp);
        notificationSettingForRegion(userId, favRegion);
        notification(userId, complexId, legalDongCd, tradeId);
    }

    private long insert(String sql, Object... args) {
        KeyHolder keys = new GeneratedKeyHolder();
        jdbc.update(connection -> {
            PreparedStatement ps = connection.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS);
            for (int i = 0; i < args.length; i++) {
                ps.setObject(i + 1, args[i]);
            }
            return ps;
        }, keys);
        Number key = keys.getKey();
        return key == null ? -1L : key.longValue();
    }
}
