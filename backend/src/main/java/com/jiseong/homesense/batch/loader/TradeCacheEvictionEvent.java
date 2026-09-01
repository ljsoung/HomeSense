package com.jiseong.homesense.batch.loader;

import java.util.Set;

/**
 * TradeDataLoader(BAT-LOD-01)가 loadBatch() 처리를 마친 뒤, 실제로 INSERT/UPDATE된 행들이 참조한
 * complexId·legalDongCd 집합을 담아 발행한다(FR-7.2). COM-CACHE-01(아직 미구현 — common.cache 패키지
 * 자체가 없다)이 이 이벤트를 구독해 complexDetail::{complexId}·regionAutocomplete::{query} 등 관련
 * Redis 캐시를 evict할 예정이다. 구독자가 없는 동안은 조용히 소비되지 않을 뿐, 발행 시점 자체는
 * CLAUDE.md 배치 파이프라인 표의 "적재 + 캐시 무효화 이벤트 발행" 순서대로 지금 고정해 둔다
 * (TradeCollectionCompletedEvent와 동일한 선구축 패턴).
 */
public record TradeCacheEvictionEvent(Set<Long> complexIds, Set<String> legalDongCds) {
}
