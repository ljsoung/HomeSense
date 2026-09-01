package com.jiseong.homesense.batch.loader;

import java.util.Set;

/**
 * TradeChunkLoader.loadChunk() 한 번의 결과. LoadResult(카운트)와, 이 청크에서 실제로 INSERT/UPDATE된
 * 행들이 참조한 complexId/legalDongCd 집합(캐시 무효화 이벤트 발행용)을 함께 묶는다.
 */
record ChunkOutcome(LoadResult result, Set<Long> touchedComplexIds, Set<String> touchedLegalDongCds) {
}
