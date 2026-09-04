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
        when(cacheManager.getCache("complexDetail")).thenReturn(complexDetailCache);
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
        verify(cacheManager, never()).getCache("complexDetail");
        verify(cacheManager, never()).getCache("popularComplexes");
    }

    @Test
    void 캐시를_찾지_못해도_예외없이_넘어간다() {
        when(cacheManager.getCache("complexDetail")).thenReturn(null);
        when(cacheManager.getCache("popularComplexes")).thenReturn(null);

        listener().onTradeLoaded(new TradeCacheEvictionEvent(Set.of(1L), Set.of()));

        verify(cacheManager).getCache("complexDetail");
    }
}
