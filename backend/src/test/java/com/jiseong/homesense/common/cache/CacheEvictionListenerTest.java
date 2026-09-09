package com.jiseong.homesense.common.cache;

import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.Set;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.cache.Cache;
import org.springframework.cache.CacheManager;

import com.jiseong.homesense.batch.loader.TradeCacheEvictionEvent;
import com.jiseong.homesense.batch.matcher.LegalDistrictCodeReloadedEvent;

@ExtendWith(MockitoExtension.class)
class CacheEvictionListenerTest {

    @Mock
    private CacheManager cacheManager;

    private CacheEvictionListener listener() {
        return new CacheEvictionListener(cacheManager);
    }

    @Test
    void complexId마다_complexDetail_캐시를_evict하고_popularComplexes는_통째로_clear한다() {
        Cache complexDetailCache = mock(Cache.class);
        Cache popularComplexesCache = mock(Cache.class);
        when(cacheManager.getCache("complexDetailV2")).thenReturn(complexDetailCache);
        when(cacheManager.getCache("popularComplexes")).thenReturn(popularComplexesCache);

        listener().onTradeLoaded(new TradeCacheEvictionEvent(Set.of(1L, 2L), Set.of()));

        verify(complexDetailCache).evict(eq(1L));
        verify(complexDetailCache).evict(eq(2L));
        verify(popularComplexesCache).clear();
    }

    @Test
    void complexId가_없으면_legalDongCd가_있어도_아무_캐시도_건드리지_않는다() {
        listener().onTradeLoaded(new TradeCacheEvictionEvent(Set.of(), Set.of("1168010100")));

        verify(cacheManager, never()).getCache(anyString());
    }

    @Test
    void regionAutocomplete는_TradeCacheEvictionEvent로_evict되지_않는다() {
        listener().onTradeLoaded(new TradeCacheEvictionEvent(Set.of(1L), Set.of("1168010100")));

        verify(cacheManager, never()).getCache("regionAutocomplete");
    }

    @Test
    void 법정동코드가_재적재되면_regionAutocomplete를_통째로_clear한다() {
        Cache regionAutocompleteCache = mock(Cache.class);
        when(cacheManager.getCache("regionAutocomplete")).thenReturn(regionAutocompleteCache);

        listener().onLegalDistrictCodeReloaded(new LegalDistrictCodeReloadedEvent());

        verify(regionAutocompleteCache).clear();
        verify(cacheManager, never()).getCache("complexDetailV2");
        verify(cacheManager, never()).getCache("popularComplexes");
    }

    @Test
    void 캐시를_찾지_못해도_예외없이_넘어간다() {
        when(cacheManager.getCache("complexDetailV2")).thenReturn(null);
        when(cacheManager.getCache("popularComplexes")).thenReturn(null);

        listener().onTradeLoaded(new TradeCacheEvictionEvent(Set.of(1L), Set.of()));

        verify(cacheManager).getCache("complexDetailV2");
    }

    @Test
    void 캐시_인프라_장애로_evict가_실패해도_예외를_전파하지_않는다() {
        // 회귀 테스트: TradeDataLoader.loadBatch()는 청크가 전부 커밋된 뒤에만 이 이벤트를 발행한다.
        // 여기서 예외가 새어나가면 publishEvent() 호출자가 이미 계산해 둔 LoadResult를 반환하지 못하고
        // 예외로 대체돼, BAT-SCH-01이 이미 커밋된 적재 건을 "0건 처리"로 batch_log에 잘못 기록한다
        // (Codex 코드리뷰 P2 지적).
        Cache complexDetailCache = mock(Cache.class);
        when(cacheManager.getCache("complexDetailV2")).thenReturn(complexDetailCache);
        when(cacheManager.getCache("popularComplexes")).thenThrow(new RuntimeException("Redis 연결 실패"));

        listener().onTradeLoaded(new TradeCacheEvictionEvent(Set.of(1L), Set.of()));

        verify(complexDetailCache).evict(1L);
    }

    @Test
    void 법정동코드_재적재_캐시_무효화가_실패해도_예외를_전파하지_않는다() {
        when(cacheManager.getCache("regionAutocomplete")).thenThrow(new RuntimeException("Redis 연결 실패"));

        listener().onLegalDistrictCodeReloaded(new LegalDistrictCodeReloadedEvent());

        verify(cacheManager).getCache("regionAutocomplete");
    }
}
