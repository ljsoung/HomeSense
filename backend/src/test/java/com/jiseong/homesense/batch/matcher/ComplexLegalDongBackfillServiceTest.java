package com.jiseong.homesense.batch.matcher;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.LocalDate;
import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.cache.Cache;
import org.springframework.cache.CacheManager;
import org.springframework.transaction.support.TransactionCallback;
import org.springframework.transaction.support.TransactionTemplate;

import com.jiseong.homesense.common.cache.CacheNames;
import com.jiseong.homesense.complex.repository.ComplexRepository;
import com.jiseong.homesense.region.entity.LegalDistrictCode;
import com.jiseong.homesense.region.repository.LegalDistrictCodeRepository;
import com.jiseong.homesense.trade.repository.TradeRepository;

/**
 * 해석 규칙은 {@link ComplexLegalDongResolverTest}가 검증한다 — 이 클래스는 apply가 끝난 뒤 캐시를 비우는지만 본다.
 * complexDetailV3는 legalDongCd와 matchPending(legal_dong_cd로 계산)을 담아, 백필 후 비우지 않으면 TTL(24h)
 * 동안 옛 값이 나간다.
 */
@ExtendWith(MockitoExtension.class)
class ComplexLegalDongBackfillServiceTest {

    @Mock
    private ComplexRepository complexRepository;

    @Mock
    private LegalDistrictCodeRepository legalDistrictCodeRepository;

    @Mock
    private TradeRepository tradeRepository;

    @Mock
    private TransactionTemplate transactionTemplate;

    @Mock
    private CacheManager cacheManager;

    @Mock
    private Cache cache;

    @InjectMocks
    private ComplexLegalDongBackfillService service;

    @BeforeEach
    void setUp() {
        when(legalDistrictCodeRepository.findAll()).thenReturn(List.of(
                code("4111100000", "경기도", "수원시 장안구", null),
                code("4111112900", "경기도", "수원시 장안구", "파장동")));
        List<Object[]> targets = List.<Object[]>of(
                new Object[] { 1L, "경기도", "수원장안구", "파장동", "경기도 수원장안구 파장동 199 궁전아파트" });
        when(complexRepository.findAddressFieldsWithoutLegalDongCd()).thenReturn(targets);
        when(cacheManager.getCache(anyString())).thenReturn(cache);
    }

    private static LegalDistrictCode code(String cd, String sido, String sigungu, String emd) {
        String name = String.join(" ", java.util.stream.Stream.of(sido, sigungu, emd).filter(p -> p != null).toList());
        return LegalDistrictCode.builder().legalDongCd(cd).legalDongName(name).sidoName(sido).sigunguName(sigungu)
                .eupmyeondongName(emd).isActive(true).dataVersion(LocalDate.of(2026, 9, 17)).build();
    }

    @SuppressWarnings("unchecked")
    private void runChunksInline() {
        when(transactionTemplate.execute(any()))
                .thenAnswer(inv -> ((TransactionCallback<Integer>) inv.getArgument(0)).doInTransaction(null));
    }

    @Test
    void apply가_끝나면_상세_인기단지_자동완성_캐시를_전체_비운다() {
        runChunksInline();
        when(complexRepository.fillLegalDongCdIfNull(eq(1L), eq("4111112900"), any())).thenReturn(1);

        service.apply(ComplexLegalDongBackfillService.Strategy.COMBINED);

        verify(cacheManager).getCache(CacheNames.COMPLEX_DETAIL);
        verify(cacheManager).getCache(CacheNames.POPULAR_COMPLEXES);
        verify(cacheManager).getCache(CacheNames.REGION_AUTOCOMPLETE);
        verify(cache, times(3)).invalidate();
    }

    @Test
    void 청크_갱신이_실패해도_이미_커밋된_청크가_있을_수_있어_캐시를_비우고_예외는_그대로_던진다() {
        when(transactionTemplate.execute(any())).thenThrow(new IllegalStateException("DB 장애"));

        assertThatThrownBy(() -> service.apply(ComplexLegalDongBackfillService.Strategy.COMBINED))
                .isInstanceOf(IllegalStateException.class);

        verify(cache, times(3)).invalidate();
    }

    @Test
    void 캐시_비우기_실패는_흡수하고_나머지_캐시도_계속_비운다() {
        runChunksInline();
        when(complexRepository.fillLegalDongCdIfNull(any(), any(), any())).thenReturn(1);
        Cache failing = mock(Cache.class);
        doThrow(new IllegalStateException("Redis 장애")).when(failing).invalidate();
        when(cacheManager.getCache(CacheNames.COMPLEX_DETAIL)).thenReturn(failing);

        service.apply(ComplexLegalDongBackfillService.Strategy.COMBINED);

        verify(failing).invalidate();
        verify(cache, times(2)).invalidate();
        verify(tradeRepository, never()).countLegalDongCdByComplex();
    }
}
