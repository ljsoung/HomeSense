package com.jiseong.homesense.batch.loader;

/**
 * TradeDataLoader.loadBatch()의 반환값. BAT-SCH-01이 이 값을 그대로 batch_log의
 * processed_count/error_count에 기록한다. processedCount는 성공적으로 적재된(inserted+updated) 건수이고,
 * errorCount는 재시도까지 실패해 스킵된 건수다.
 */
public record LoadResult(int processedCount, int errorCount, int inserted, int updated) {

    static LoadResult empty() {
        return new LoadResult(0, 0, 0, 0);
    }

    LoadResult merge(LoadResult other) {
        return new LoadResult(
                processedCount + other.processedCount(),
                errorCount + other.errorCount(),
                inserted + other.inserted(),
                updated + other.updated());
    }
}
