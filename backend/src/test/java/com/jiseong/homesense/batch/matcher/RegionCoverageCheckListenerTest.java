package com.jiseong.homesense.batch.matcher;

import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

/**
 * CLAUDE.md "CacheEvictionListener의 두 리스너는 반드시 자기 몸통을 try-catch로 감싸야 한다" 원칙과
 * 같은 이유 — 이 체크가 던지는 예외가 이미 커밋된 재적재 결과나 같은 이벤트를 구독하는 다른 리스너
 * (regionAutocomplete 캐시 evict)에 영향을 주면 안 된다.
 */
@ExtendWith(MockitoExtension.class)
class RegionCoverageCheckListenerTest {

    @Mock
    private RegionCoverageChecker regionCoverageChecker;

    private RegionCoverageCheckListener listener() {
        return new RegionCoverageCheckListener(regionCoverageChecker);
    }

    @Test
    void 법정동코드가_재적재되면_커버리지_체크를_실행한다() {
        listener().onLegalDistrictCodeReloaded(new LegalDistrictCodeReloadedEvent());

        verify(regionCoverageChecker).checkCoverage();
    }

    @Test
    void 커버리지_체크가_예외를_던져도_전파하지_않는다() {
        when(regionCoverageChecker.checkCoverage()).thenThrow(new RuntimeException("DB 조회 실패"));

        listener().onLegalDistrictCodeReloaded(new LegalDistrictCodeReloadedEvent());

        verify(regionCoverageChecker).checkCoverage();
    }
}
