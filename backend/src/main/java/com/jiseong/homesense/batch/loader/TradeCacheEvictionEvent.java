package com.jiseong.homesense.batch.loader;

import java.util.Set;

/**
 * TradeDataLoader(BAT-LOD-01)가 loadBatch() 처리를 마친 뒤, 실제로 INSERT/UPDATE된 행들이 참조한
 * complexId·legalDongCd 집합을 담아 발행한다(FR-7.2). COM-CACHE-01(CacheEvictionListener)이 이
 * 이벤트를 구독해 complexId별 complexDetail::{complexId}를 evict하고, complexId가 하나라도 있으면
 * popularComplexes 전체를 evict한다.
 *
 * <p>regionAutocomplete는 이 이벤트가 담은 legalDongCds로 evict하지 않는다 — regionAutocomplete는
 * BAT-MAT-01(LegalDistrictCodeLoader)의 법정동코드 재적재(비정기)에만 반응해야 하는데, 이 이벤트는
 * BAT-LOD-01(일 1회 이상)마다 발행돼 트리거 주기가 너무 잦다. legalDongCds 필드는 지역 기반 알림
 * 평가(BAT-NTF-01, 4단계) 등 다른 구독자를 위해 남겨둔다.
 */
public record TradeCacheEvictionEvent(Set<Long> complexIds, Set<String> legalDongCds) {
}
