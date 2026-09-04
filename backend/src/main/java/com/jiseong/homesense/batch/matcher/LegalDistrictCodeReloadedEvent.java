package com.jiseong.homesense.batch.matcher;

/**
 * LegalDistrictCodeLoader(BAT-MAT-01)가 법정동코드 CSV를 최초/재적재하고 커밋을 마친 뒤 발행한다.
 * COM-CACHE-01(CacheEvictionListener)이 이 이벤트를 구독해 regionAutocomplete 캐시 전체를 evict한다.
 *
 * <p>trade 적재(BAT-LOD-01, 일 1회 이상)와 달리 법정동코드 재적재는 비정기적으로만 일어나므로,
 * regionAutocomplete는 일 단위로 도는 TradeCacheEvictionEvent가 아니라 이 이벤트에 물려야
 * "정적 데이터라 TTL을 오래 가져가려는" 설계 의도가 성립한다 — 어떤 legalDongCd가 바뀌었는지와
 * 무관하게 전체 evict인 이유도 같다(어떤 캐시된 autocomplete 쿼리 문자열이 영향받는지는 역산할
 * 수 없다).
 */
public record LegalDistrictCodeReloadedEvent() {
}
