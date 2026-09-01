package com.jiseong.homesense.batch.loader;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Component;

import com.jiseong.homesense.batch.parser.dto.TradeDraft;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * BAT-LOD-01. 정제·매칭이 끝난 TradeDraft 목록을 dedup_hash 기준 upsert로 적재한다.
 * "정제·매칭이 끝났다"는 것은 complexId/legalDongCd/matchMethod/matchConfidence가 이미 채워져 있다는
 * 뜻이다 — 이 클래스는 그 전제를 그대로 신뢰할 뿐, BAT-MAT-01(LegalDistrictMatcher)→BAT-MAT-02
 * (ComplexMasterMatcher)를 실제로 체이닝해 TradeDraft에 매칭 결과를 채워 넘기는 오케스트레이션
 * (Spring Batch Step 배선 또는 임시 코디네이터)은 이 클래스의 책임이 아니다 — 별도 후속 작업이다.
 * 500건 청크 단위로 TradeChunkLoader에 위임해 트랜잭션을 나누고, 한 청크의 커밋 실패가 다른 청크나
 * 전체 loadBatch 호출을 막지 않도록 청크 경계에서 예외를 흡수한다.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class TradeDataLoader {

    private static final int CHUNK_SIZE = 500;

    private final TradeChunkLoader tradeChunkLoader;
    private final ApplicationEventPublisher eventPublisher;

    public LoadResult loadBatch(List<TradeDraft> drafts) {
        LoadResult total = LoadResult.empty();
        Set<Long> touchedComplexIds = new LinkedHashSet<>();
        Set<String> touchedLegalDongCds = new LinkedHashSet<>();

        for (List<TradeDraft> chunk : partition(drafts, CHUNK_SIZE)) {
            try {
                ChunkOutcome outcome = tradeChunkLoader.loadChunk(chunk);
                total = total.merge(outcome.result());
                touchedComplexIds.addAll(outcome.touchedComplexIds());
                touchedLegalDongCds.addAll(outcome.touchedLegalDongCds());
            } catch (RuntimeException e) {
                // 청크 트랜잭션 자체가 커밋에 실패한 경우(개별 건 재시도로 못 거르는 예외적 상황) —
                // 이 청크는 전체 롤백되지만 다음 청크는 계속 진행한다. 부분 성공분을 알 수 없으므로
                // 청크 전체 건수를 실패로 집계한다.
                log.error("BAT-LOD-01 청크 적재 실패, 이 청크는 전체 롤백되고 다음 청크로 계속 진행한다: size={}",
                        chunk.size(), e);
                total = total.merge(new LoadResult(0, chunk.size(), 0, 0));
            }
        }

        if (!touchedComplexIds.isEmpty() || !touchedLegalDongCds.isEmpty()) {
            eventPublisher.publishEvent(new TradeCacheEvictionEvent(touchedComplexIds, touchedLegalDongCds));
        }

        log.info("BAT-LOD-01 적재 완료: processedCount={}, errorCount={}, inserted={}, updated={}",
                total.processedCount(), total.errorCount(), total.inserted(), total.updated());
        return total;
    }

    private List<List<TradeDraft>> partition(List<TradeDraft> drafts, int size) {
        List<List<TradeDraft>> chunks = new ArrayList<>();
        for (int i = 0; i < drafts.size(); i += size) {
            chunks.add(drafts.subList(i, Math.min(i + size, drafts.size())));
        }
        return chunks;
    }
}
