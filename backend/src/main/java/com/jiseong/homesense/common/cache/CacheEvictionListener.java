package com.jiseong.homesense.common.cache;

import org.springframework.cache.Cache;
import org.springframework.cache.CacheManager;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

import com.jiseong.homesense.batch.loader.TradeCacheEvictionEvent;
import com.jiseong.homesense.batch.matcher.LegalDistrictCodeReloadedEvent;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * COM-CACHE-01. 서로 주기가 다른 두 배치 이벤트를 구독해 각각 관련 캐시만 evict한다(FR-7.2).
 *
 * <p>두 리스너 모두 {@code @TransactionalEventListener(AFTER_COMMIT)}로 받는다. 지금 시점에서
 * TradeDataLoader.loadBatch()는 그 자체가 {@code @Transactional}이 아니라 청크별
 * {@code @Transactional}(TradeChunkLoader.loadChunk())이 전부 끝난 뒤에만 이벤트를 발행해 커밋 후
 * 실행이 실질적으로 보장되지만, LegalDistrictCodeLoader.loadInitial()은 {@code @Transactional}
 * 메서드 안에서 발행해 일반 {@code @EventListener}로 받으면 커밋 전에 동기 실행돼 evict가
 * 무의미해진다(evict 직후~커밋 사이에 다른 트랜잭션이 구버전 행을 읽어 캐시를 재적재할 수 있음).
 * 설계서 4.6절 각주가 예고한 대로 이 파이프라인이 나중에 Spring Batch 청크 지향 Step으로
 * 옮겨가면 Step 자체가 청크 처리를 트랜잭션으로 감싸는 게 기본 동작이라 "loadBatch는
 * non-transactional"이라는 지금의 전제가 깨질 수 있다 — 그때 가서 onTradeLoaded도 같은 버그를
 * 재현하지 않도록 두 리스너를 미리 통일해 둔다. fallbackExecution=true는 트랜잭션 없이 이벤트가
 * 발행되는 지금의 TradeCacheEvictionEvent 같은 경우 AFTER_COMMIT을 그냥 건너뛰고 즉시 실행되게
 * 하는 안전장치다(활성 트랜잭션이 없으면 조용히 유실되는 게 기본 동작이므로).
 *
 * <ul>
 *   <li>TradeCacheEvictionEvent(BAT-LOD-01, 일 1회 이상) — complexDetailV2::{complexId}를 건별로
 *       evict하고, complexId가 하나라도 있으면 popularComplexes(인기 단지 순위) 전체를 evict한다.
 *       complexId 하나만으로 "어떤 limit으로 캐시된 popularComplexes 항목이 영향받는지"를 역산할 수
 *       없어 전체 clear()로 처리한다.</li>
 *   <li>LegalDistrictCodeReloadedEvent(BAT-MAT-01, 비정기) — regionAutocomplete(지역 자동완성) 전체를
 *       evict한다. 이 캐시를 TradeCacheEvictionEvent에 묶으면 정적 데이터를 매일 무효화하게 돼
 *       TTL을 길게 가져가려는 설계 의도가 깨지므로 반드시 이 이벤트에만 반응해야 한다.</li>
 * </ul>
 *
 * <p>{@code COMPLEX_DETAIL_CACHE}가 {@code complexDetail}이 아니라 {@code complexDetailV2}인 이유는
 * {@link com.jiseong.homesense.complex.service.ComplexDetailCache} 참고 — housingType 필드 추가로
 * 캐시 이름을 버전업했다.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class CacheEvictionListener {

    private static final String COMPLEX_DETAIL_CACHE = "complexDetailV2";
    private static final String POPULAR_COMPLEXES_CACHE = "popularComplexes";
    private static final String REGION_AUTOCOMPLETE_CACHE = "regionAutocomplete";

    private final CacheManager cacheManager;

    /**
     * 이 이벤트는 관련 trade 행이 이미 커밋된 뒤에만 발행된다(TradeDataLoader.loadBatch()가 청크 처리를
     * 모두 마친 뒤 발행) — evict 실패(예: Redis 장애로 인한 RuntimeException)가 그대로 새어나가면
     * publishEvent() 호출자(TradeDataLoader.loadBatch())가 이미 계산해 둔 실제 처리 건수(LoadResult)를
     * 반환하지 못하고 예외로 대체된다. BAT-SCH-01(TradeIngestionPipeline 경유)이 이 예외를 잡아 해당
     * 데이터셋을 "0건 처리, 0건 에러"로 batch_log에 기록하는데, 실제로는 trade 테이블에 이미 반영된
     * 적재 결과와 모순돼 운영자가 batch_log만 보고 부분/전체 성공을 놓치게 된다(Codex 코드리뷰 P2 지적).
     * 캐시 무효화는 최악의 경우 TTL(24h) 동안 최신화가 늦어질 뿐인 부수 효과라 커밋된 적재 결과의
     * 정확한 기록보다 우선할 수 없다 — 그래서 evict 실패는 여기서 흡수하고 로그만 남긴다.
     */
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT, fallbackExecution = true)
    public void onTradeLoaded(TradeCacheEvictionEvent event) {
        if (event.complexIds().isEmpty()) {
            return;
        }

        try {
            event.complexIds().forEach(this::evictComplexDetail);
            clearCache(POPULAR_COMPLEXES_CACHE);
            log.info("COM-CACHE-01 트레이드 적재발 캐시 무효화 완료: complexIds={}", event.complexIds().size());
        } catch (RuntimeException e) {
            log.error("COM-CACHE-01 트레이드 적재발 캐시 무효화 실패 — 이미 커밋된 적재 결과에는 영향 없음, "
                    + "캐시는 TTL 만료까지 stale할 수 있다: complexIds={}", event.complexIds().size(), e);
        }
    }

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT, fallbackExecution = true)
    public void onLegalDistrictCodeReloaded(LegalDistrictCodeReloadedEvent event) {
        try {
            clearCache(REGION_AUTOCOMPLETE_CACHE);
            log.info("COM-CACHE-01 법정동코드 재적재발 캐시 무효화 완료: cache={}", REGION_AUTOCOMPLETE_CACHE);
        } catch (RuntimeException e) {
            log.error("COM-CACHE-01 법정동코드 재적재발 캐시 무효화 실패 — 캐시는 TTL 만료까지 stale할 수 있다: cache={}",
                    REGION_AUTOCOMPLETE_CACHE, e);
        }
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
