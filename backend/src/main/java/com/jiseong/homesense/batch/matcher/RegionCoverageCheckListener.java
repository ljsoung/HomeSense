package com.jiseong.homesense.batch.matcher;

import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * {@link LegalDistrictCodeReloadedEvent}를 구독해 재적재 직후 {@link RegionCoverageChecker}를 실행한다
 * — 법정동코드 참조자료가 최신화될 때마다(비정기) 자동으로 커버리지 공백을 로그에 남긴다.
 *
 * <p>{@code CacheEvictionListener.onLegalDistrictCodeReloaded()}와 같은 이유로
 * {@code @TransactionalEventListener(AFTER_COMMIT)}로 받는다 — {@code LegalDistrictCodeLoader.loadInitial()}이
 * {@code @Transactional} 메서드 안에서 이벤트를 발행하므로, 커밋 전에 조회하면 아직 반영되지 않은
 * 구버전 데이터를 기준으로 잘못된 공백을 보고할 수 있다.
 *
 * <p>이 체크가 던지는 예외가 재적재 자체(이미 커밋된 결과)나 같은 이벤트를 구독하는 다른 리스너
 * (regionAutocomplete 캐시 evict)에 영향을 주면 안 되므로 반드시 몸통을 try-catch로 감싼다 —
 * CLAUDE.md의 "CacheEvictionListener의 두 리스너는 반드시 자기 몸통을 try-catch로 감싸야 한다" 원칙과
 * 같은 이유다.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class RegionCoverageCheckListener {

    private final RegionCoverageChecker regionCoverageChecker;

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT, fallbackExecution = true)
    public void onLegalDistrictCodeReloaded(LegalDistrictCodeReloadedEvent event) {
        try {
            regionCoverageChecker.checkCoverage();
        } catch (RuntimeException e) {
            log.error("BAT-MAT-01 지역 커버리지 체크 실행 중 오류 — 재적재 자체는 이미 커밋되어 영향 없음", e);
        }
    }
}
