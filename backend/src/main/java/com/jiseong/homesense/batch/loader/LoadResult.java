package com.jiseong.homesense.batch.loader;

import java.util.LinkedHashSet;
import java.util.Set;

/**
 * TradeDataLoader.loadBatch()의 반환값. BAT-SCH-01이 이 값을 그대로 batch_log의
 * processed_count/error_count에 기록한다. processedCount는 성공적으로 적재된(inserted+updated) 건수이고,
 * errorCount는 재시도까지 실패해 스킵된 건수다.
 *
 * <p>touchedComplexIds/touchedLegalDongCds는 이번 적재로 INSERT/UPDATE된 행이 참조한 단지·법정동 집합이다.
 * BAT-SCH-01이 런 단위로 모아 BAT-NTF-01(WatchConditionEvaluator)의 평가 범위를 좁히는 데 쓴다 — 캐시 무효화
 * 이벤트({@link TradeCacheEvictionEvent})가 담는 집합과 같다. batch_log에는 기록하지 않는다.
 */
public record LoadResult(int processedCount, int errorCount, int inserted, int updated,
                         Set<Long> touchedComplexIds, Set<String> touchedLegalDongCds) {

    public LoadResult {
        touchedComplexIds = Set.copyOf(touchedComplexIds);
        touchedLegalDongCds = Set.copyOf(touchedLegalDongCds);
    }

    /** 영향 집합이 없는 결과(카운트만). */
    public LoadResult(int processedCount, int errorCount, int inserted, int updated) {
        this(processedCount, errorCount, inserted, updated, Set.of(), Set.of());
    }

    static LoadResult empty() {
        return new LoadResult(0, 0, 0, 0);
    }

    LoadResult merge(LoadResult other) {
        return new LoadResult(
                processedCount + other.processedCount(),
                errorCount + other.errorCount(),
                inserted + other.inserted(),
                updated + other.updated(),
                union(touchedComplexIds, other.touchedComplexIds()),
                union(touchedLegalDongCds, other.touchedLegalDongCds()));
    }

    LoadResult withTouched(Set<Long> complexIds, Set<String> legalDongCds) {
        return new LoadResult(processedCount, errorCount, inserted, updated, complexIds, legalDongCds);
    }

    private static <T> Set<T> union(Set<T> a, Set<T> b) {
        if (b.isEmpty()) {
            return a;
        }
        Set<T> merged = new LinkedHashSet<>(a);
        merged.addAll(b);
        return merged;
    }
}
