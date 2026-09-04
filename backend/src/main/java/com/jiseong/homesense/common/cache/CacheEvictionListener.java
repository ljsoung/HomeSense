package com.jiseong.homesense.common.cache;

import org.springframework.cache.Cache;
import org.springframework.cache.CacheManager;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

import com.jiseong.homesense.batch.loader.TradeCacheEvictionEvent;
import com.jiseong.homesense.batch.matcher.LegalDistrictCodeReloadedEvent;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * COM-CACHE-01. 서로 주기가 다른 두 배치 이벤트를 구독해 각각 관련 캐시만 evict한다(FR-7.2).
 *
 * <ul>
 *   <li>TradeCacheEvictionEvent(BAT-LOD-01, 일 1회 이상) — complexDetail::{complexId}를 건별로
 *       evict하고, complexId가 하나라도 있으면 popularComplexes(인기 단지 순위) 전체를 evict한다.
 *       complexId 하나만으로 "어떤 limit으로 캐시된 popularComplexes 항목이 영향받는지"를 역산할 수
 *       없어 전체 clear()로 처리한다.</li>
 *   <li>LegalDistrictCodeReloadedEvent(BAT-MAT-01, 비정기) — regionAutocomplete(지역 자동완성) 전체를
 *       evict한다. 이 캐시를 TradeCacheEvictionEvent에 묶으면 정적 데이터를 매일 무효화하게 돼
 *       TTL을 길게 가져가려는 설계 의도가 깨지므로 반드시 이 이벤트에만 반응해야 한다.</li>
 * </ul>
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class CacheEvictionListener {

    private static final String COMPLEX_DETAIL_CACHE = "complexDetail";
    private static final String POPULAR_COMPLEXES_CACHE = "popularComplexes";
    private static final String REGION_AUTOCOMPLETE_CACHE = "regionAutocomplete";

    private final CacheManager cacheManager;

    @EventListener
    public void onTradeLoaded(TradeCacheEvictionEvent event) {
        if (event.complexIds().isEmpty()) {
            return;
        }

        event.complexIds().forEach(this::evictComplexDetail);
        clearCache(POPULAR_COMPLEXES_CACHE);

        log.info("COM-CACHE-01 트레이드 적재발 캐시 무효화 완료: complexIds={}", event.complexIds().size());
    }

    @EventListener
    public void onLegalDistrictCodeReloaded(LegalDistrictCodeReloadedEvent event) {
        clearCache(REGION_AUTOCOMPLETE_CACHE);
        log.info("COM-CACHE-01 법정동코드 재적재발 캐시 무효화 완료: cache={}", REGION_AUTOCOMPLETE_CACHE);
    }

    private void evictComplexDetail(Long complexId) {
        Cache cache = cacheManager.getCache(COMPLEX_DETAIL_CACHE);
        if (cache == null) {
            log.warn("COM-CACHE-01 '{}' 캐시를 찾을 수 없어 evict를 건너뛴다: complexId={}",
                    COMPLEX_DETAIL_CACHE, complexId);
            return;
        }
        cache.evict(complexId);
    }

    private void clearCache(String cacheName) {
        Cache cache = cacheManager.getCache(cacheName);
        if (cache == null) {
            log.warn("COM-CACHE-01 '{}' 캐시를 찾을 수 없어 clear를 건너뛴다.", cacheName);
            return;
        }
        cache.clear();
    }
}
