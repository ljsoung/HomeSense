package com.jiseong.homesense.batch.notifier;

import java.math.BigDecimal;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Function;

import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Component;

import com.jiseong.homesense.trade.entity.DealCategory;
import com.jiseong.homesense.trade.entity.RentType;

import lombok.RequiredArgsConstructor;

/**
 * BAT-NTF-01 전용 읽기 쿼리(D11). 알림 설정 ⨝ 관심 매물/지역 ⨝ 회원, 거래 집계처럼 어느 한 도메인에 속하지 않는
 * 교차 조회라 batch.notifier가 소유한다(batch.matcher가 complex·법정동 리포지토리를 직접 읽는 것과 같은 방향).
 * 모두 projection을 돌려주는 네이티브 SQL이고 엔티티를 로딩하지 않는다.
 *
 * <p>IN 목록은 {@value #IN_CHUNK}개, LIKE OR 묶음은 {@value #LIKE_CHUNK}개 단위로 나눠 보낸다. 항목 수와
 * 무관하게 쿼리 수가 묶음 수로 고정돼 N+1이 없다. 주택유형 조건은 걸지 않는다 — 연립다세대가 적재되기
 * 시작해도 코드 변경 없이 함께 평가된다(관심 지역은 유형 합산, D4).
 */
@Component
@RequiredArgsConstructor
class WatchConditionQuery {

    static final int IN_CHUNK = 1000;
    static final int LIKE_CHUNK = 200;

    /** SVC-RGN-01·TradeRepository와 같은 거래별 3.3㎡당 가격식. */
    private static final String PER_PYEONG_SUM = "SUM(t.deal_amount / (t.exclu_use_area / 3.3058))";

    private static final String SALE_PRICE_CONDITION = """
            t.deal_category = 'SALE' AND t.cancel_yn = FALSE AND t.deal_amount IS NOT NULL
            AND t.exclu_use_area > 0
            AND t.deal_date >= :from AND t.deal_date < :to
            AND t.created_at < :runStartedAt
            """;

    private final NamedParameterJdbcTemplate jdbc;

    /** 이번 런에 거래가 적재된 단지를 관심 매물로 둔 ACTIVE 회원의 알림 설정. */
    List<WatchTarget> findPropertyTargets(Collection<Long> complexIds) {
        return inChunks(complexIds, chunk -> jdbc.query("""
                SELECT ns.notification_setting_id, ns.user_id, fp.complex_id, c.complex_name,
                       ns.price_change_threshold_pct, ns.new_trade_alert_yn
                FROM notification_setting ns
                JOIN favorite_property fp ON fp.favorite_property_id = ns.favorite_property_id
                JOIN complex c ON c.complex_id = fp.complex_id
                JOIN user u ON u.user_id = ns.user_id
                WHERE fp.complex_id IN (:ids) AND u.status = 'ACTIVE'
                """, new MapSqlParameterSource("ids", chunk), (rs, i) -> new WatchTarget(
                rs.getLong("notification_setting_id"),
                rs.getLong("user_id"),
                rs.getLong("complex_id"),
                null,
                rs.getString("complex_name"),
                rs.getBigDecimal("price_change_threshold_pct"),
                rs.getBoolean("new_trade_alert_yn"))));
    }

    /** 관심 지역 코드가 후보(거래 코드와 그 상위 코드, {@link LegalDongHierarchy})에 드는 ACTIVE 회원의 알림 설정. */
    List<WatchTarget> findRegionTargets(Collection<String> candidateCodes) {
        return inChunks(candidateCodes, chunk -> jdbc.query("""
                SELECT ns.notification_setting_id, ns.user_id, fr.legal_dong_cd, l.legal_dong_name,
                       ns.price_change_threshold_pct, ns.new_trade_alert_yn
                FROM notification_setting ns
                JOIN favorite_region fr ON fr.favorite_region_id = ns.favorite_region_id
                JOIN legal_district_code l ON l.legal_dong_cd = fr.legal_dong_cd
                JOIN user u ON u.user_id = ns.user_id
                WHERE fr.legal_dong_cd IN (:codes) AND u.status = 'ACTIVE'
                """, new MapSqlParameterSource("codes", chunk), (rs, i) -> new WatchTarget(
                rs.getLong("notification_setting_id"),
                rs.getLong("user_id"),
                null,
                rs.getString("legal_dong_cd"),
                rs.getString("legal_dong_name"),
                rs.getBigDecimal("price_change_threshold_pct"),
                rs.getBoolean("new_trade_alert_yn"))));
    }

    /**
     * 이번 런에 신규 INSERT된(created_at >= 런 시작) 해제되지 않은 거래. 단지에 매칭된 거래는 반드시 법정동도
     * 매칭돼 있으므로(BAT-MAT-02는 BAT-MAT-01 결과로 후보를 찾는다) 법정동 집합 하나로 관심 매물·지역의 신규
     * 거래를 모두 얻는다.
     */
    List<NewTradeRow> findNewTrades(LocalDateTime runStartedAt, Collection<String> legalDongCds) {
        return inChunks(legalDongCds, chunk -> jdbc.query("""
                SELECT t.trade_id, t.complex_id, t.legal_dong_cd, t.deal_date, t.deal_category, t.rent_type,
                       t.deal_amount, t.deposit_amount, t.monthly_rent_amount, t.exclu_use_area, t.floor
                FROM trade t
                WHERE t.legal_dong_cd IN (:codes) AND t.created_at >= :runStartedAt AND t.cancel_yn = FALSE
                """, new MapSqlParameterSource("codes", chunk).addValue("runStartedAt", runStartedAt),
                (rs, i) -> new NewTradeRow(
                        rs.getLong("trade_id"),
                        nullableLong(rs, "complex_id"),
                        rs.getString("legal_dong_cd"),
                        rs.getObject("deal_date", LocalDate.class),
                        DealCategory.valueOf(rs.getString("deal_category")),
                        rs.getString("rent_type") != null ? RentType.valueOf(rs.getString("rent_type")) : null,
                        nullableLong(rs, "deal_amount"),
                        nullableLong(rs, "deposit_amount"),
                        nullableLong(rs, "monthly_rent_amount"),
                        rs.getBigDecimal("exclu_use_area"),
                        nullableShort(rs, "floor"))));
    }

    /** 단지별 기준 평균 재료 — 런 이전에 적재된, 계약일이 [from, to)인 매매 거래. */
    Map<Long, PriceAggregate> baselineByComplex(Collection<Long> complexIds, LocalDate from, LocalDate to,
                                                LocalDateTime runStartedAt) {
        Map<Long, PriceAggregate> result = new HashMap<>();
        for (List<Long> chunk : partition(List.copyOf(complexIds), IN_CHUNK)) {
            jdbc.query("SELECT t.complex_id, " + PER_PYEONG_SUM + " AS s, COUNT(*) AS n FROM trade t "
                            + "WHERE t.complex_id IN (:ids) AND " + SALE_PRICE_CONDITION + " GROUP BY t.complex_id",
                    baselineParams(from, to, runStartedAt).addValue("ids", chunk),
                    rs -> {
                        result.put(rs.getLong("complex_id"), aggregate(rs));
                    });
        }
        return result;
    }

    /**
     * 관심 지역 prefix별 기준 평균 재료. 법정동별로 GROUP BY한 뒤 prefix마다 합친다 — 한 법정동이 여러 prefix
     * (예: 동과 그 상위 시군구)에 동시에 들 수 있어 prefix마다 따로 더한다.
     */
    Map<String, PriceAggregate> baselineByPrefix(Collection<String> prefixes, LocalDate from, LocalDate to,
                                                 LocalDateTime runStartedAt) {
        Map<String, PriceAggregate> byCode = new HashMap<>();
        for (List<String> chunk : partition(List.copyOf(prefixes), LIKE_CHUNK)) {
            MapSqlParameterSource params = baselineParams(from, to, runStartedAt);
            List<String> likes = new ArrayList<>();
            for (int i = 0; i < chunk.size(); i++) {
                likes.add("t.legal_dong_cd LIKE :p" + i);
                params.addValue("p" + i, chunk.get(i) + "%");
            }
            jdbc.query("SELECT t.legal_dong_cd, " + PER_PYEONG_SUM + " AS s, COUNT(*) AS n FROM trade t "
                            + "WHERE (" + String.join(" OR ", likes) + ") AND " + SALE_PRICE_CONDITION
                            + " GROUP BY t.legal_dong_cd",
                    params,
                    rs -> {
                        byCode.merge(rs.getString("legal_dong_cd"), aggregate(rs), PriceAggregate::plus);
                    });
        }
        Map<String, PriceAggregate> byPrefix = new HashMap<>();
        for (String prefix : prefixes) {
            PriceAggregate total = PriceAggregate.EMPTY;
            for (Map.Entry<String, PriceAggregate> e : byCode.entrySet()) {
                if (e.getKey().startsWith(prefix)) {
                    total = total.plus(e.getValue());
                }
            }
            byPrefix.put(prefix, total);
        }
        return byPrefix;
    }

    private static MapSqlParameterSource baselineParams(LocalDate from, LocalDate to, LocalDateTime runStartedAt) {
        return new MapSqlParameterSource()
                .addValue("from", from)
                .addValue("to", to)
                .addValue("runStartedAt", runStartedAt);
    }

    private static PriceAggregate aggregate(ResultSet rs) throws SQLException {
        BigDecimal sum = rs.getBigDecimal("s");
        return new PriceAggregate(sum != null ? sum : BigDecimal.ZERO, rs.getLong("n"));
    }

    private static Long nullableLong(ResultSet rs, String column) throws SQLException {
        long value = rs.getLong(column);
        return rs.wasNull() ? null : value;
    }

    private static Short nullableShort(ResultSet rs, String column) throws SQLException {
        short value = rs.getShort(column);
        return rs.wasNull() ? null : value;
    }

    private static <T, R> List<R> inChunks(Collection<T> values, Function<List<T>, List<R>> query) {
        List<R> result = new ArrayList<>();
        for (List<T> chunk : partition(List.copyOf(values), IN_CHUNK)) {
            result.addAll(query.apply(chunk));
        }
        return result;
    }

    static <T> List<List<T>> partition(List<T> values, int size) {
        List<List<T>> chunks = new ArrayList<>();
        for (int i = 0; i < values.size(); i += size) {
            chunks.add(values.subList(i, Math.min(i + size, values.size())));
        }
        return chunks;
    }
}
