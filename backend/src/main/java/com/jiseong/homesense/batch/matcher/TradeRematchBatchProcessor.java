package com.jiseong.homesense.batch.matcher;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Objects;

import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import com.jiseong.homesense.batch.parser.dto.TradeDraft;
import com.jiseong.homesense.trade.entity.MatchMethod;
import com.jiseong.homesense.trade.entity.Trade;
import com.jiseong.homesense.trade.repository.TradeRepository;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * {@link TradeRematchRunner}가 한 배치(500건)씩 위임하는 실제 처리 단위. 배치 하나(조회~매칭~갱신)를
 * 정확히 하나의 트랜잭션으로 묶어 그 배치가 끝나는 즉시 커밋한다 — {@code TradeRematchRunner}가 전체
 * 커서 순회 하나를 통째로 @Transactional로 감쌌던 최초 구현은 전체 실행(수만 건, 수십 분~수 시간)이
 * 끝나야 단 한 번 커밋되는 단일 초장기 트랜잭션이 돼, (1) 중간에 프로세스가 죽거나 커넥션이 끊기면
 * 그때까지의 작업이 전부 롤백돼 사라지고, (2) 다른 커넥션(모니터링 쿼리 등)에서는 커밋 전까지 진행
 * 상황을 전혀 관측할 수 없었다(실제로 앱 로그는 재배정을 계속 찍는데도 DB의 MAX(updated_at)이
 * 수 시간째 그대로였다) — 이 클래스로 배치 단위 커밋으로 쪼개 두 문제를 모두 해결한다.
 *
 * <p>같은 클래스 내부 self-invocation으로는 {@code @Transactional} 프록시가 걸리지 않는다는 이
 * 프로젝트의 기존 함정(COM-CACHE-01 {@code ComplexDetailCache} 분리와 같은 이유, CLAUDE.md SVC-RCV-01
 * 절 참고)과 같은 이유로, 배치 조회~처리~갱신 전체를 이 별도 빈의 public 메서드 하나에 담아 항상
 * {@code TradeRematchRunner}라는 다른 빈을 통해서만 호출되도록 했다 — 그래야 프록시를 거쳐 트랜잭션이
 * 실제로 걸린다. 조회(배치 조회)와 사용(레거시 지연 로딩 필드 접근)이 반드시 같은 트랜잭션 안에서
 * 일어나야 하므로("복원된 detached 엔티티의 지연 연관관계 접근" 문제를 피하려면) 배치 조회 자체도 이
 * 메서드 안에서 수행한다 — 조회를 바깥(TradeRematchRunner)으로 빼면 그 조회가 끝나는 순간 트랜잭션이
 * 닫혀 반환된 Trade가 detached 상태가 되고, 이 메서드 안에서 {@code trade.getLegalDistrictCode()}/
 * {@code trade.getComplex()}에 접근할 때 LazyInitializationException이 난다.
 */
@Component
@RequiredArgsConstructor
@Slf4j
class TradeRematchBatchProcessor {

    static final int PAGE_SIZE = 500;

    private final TradeRepository tradeRepository;
    private final ComplexMasterMatcher complexMasterMatcher;

    /**
     * 배치 처리 결과. {@code lastTradeId}는 이 배치의 마지막 행의 trade_id(커서 전진용, 배치가
     * 비었으면 null). {@code hasMore}는 배치가 하나라도 있었는지(false면 순회 종료 신호).
     */
    record BatchOutcome(Long lastTradeId, int unchanged, int changed, boolean hasMore) {

        static BatchOutcome empty() {
            return new BatchOutcome(null, 0, 0, false);
        }
    }

    @Transactional
    BatchOutcome processUnmatchedBatch(Long cursor) {
        List<Trade> batch = tradeRepository
                .findByComplexIsNullAndLegalDistrictCodeIsNotNullAndTradeIdGreaterThanOrderByTradeIdAsc(
                        cursor, PageRequest.of(0, PAGE_SIZE));
        return process(batch);
    }

    @Transactional
    BatchOutcome processUpdatedBeforeBatch(LocalDateTime cutoff, Long cursor) {
        List<Trade> batch = tradeRepository
                .findByLegalDistrictCodeIsNotNullAndUpdatedAtBeforeAndTradeIdGreaterThanOrderByTradeIdAsc(
                        cutoff, cursor, PageRequest.of(0, PAGE_SIZE));
        return process(batch);
    }

    private BatchOutcome process(List<Trade> batch) {
        if (batch.isEmpty()) {
            return BatchOutcome.empty();
        }

        int unchanged = 0;
        int changed = 0;
        for (Trade trade : batch) {
            Long previousComplexId = trade.getComplex() == null ? null : trade.getComplex().getComplexId();
            MatchMethod previousMatchMethod = trade.getMatchMethod();

            MatchResult result = complexMasterMatcher.matchComplex(toDraft(trade), trade.getLegalDistrictCode());

            boolean same = Objects.equals(previousComplexId, result.complexId())
                    && previousMatchMethod == result.matchMethod();
            if (same) {
                unchanged++;
            } else {
                tradeRepository.applyRematch(trade.getTradeId(), result.complexId(),
                        result.matchMethod() == null ? null : result.matchMethod().name(),
                        result.matchConfidence(), LocalDateTime.now());
                changed++;
                log.info("BAT-MAT-02 재배정: tradeId={}, {}({}) -> {}({})", trade.getTradeId(),
                        previousComplexId, previousMatchMethod, result.complexId(), result.matchMethod());
            }
        }

        return new BatchOutcome(batch.get(batch.size() - 1).getTradeId(), unchanged, changed, true);
    }

    /**
     * complexId/legalDongCd/matchMethod/matchConfidence는 매처가 새로 채울 값이므로 비워 둔다 —
     * ComplexMasterMatcher.matchComplex()는 이 네 필드를 읽지 않고 legalDistrictCode 인자로만
     * 지역을 판단한다.
     */
    private TradeDraft toDraft(Trade trade) {
        return new TradeDraft(
                trade.getHousingType(),
                trade.getDealCategory(),
                trade.getRentType(),
                trade.getDatasetId(),
                trade.getSggCd(),
                trade.getUmdNm(),
                trade.getBuildingName(),
                trade.getJibun(),
                trade.getExcluUseArea(),
                trade.getFloor(),
                trade.getBuildYear(),
                trade.getDealDate(),
                trade.getDealAmount(),
                trade.getDepositAmount(),
                trade.getMonthlyRentAmount(),
                trade.getAptDong(),
                trade.getDealingType(),
                trade.getAgentSggNm(),
                trade.getRegistrationDate(),
                trade.getSellerType(),
                trade.getBuyerType(),
                trade.getLandLeaseYn(),
                trade.isCancelYn(),
                trade.getCancelDate(),
                null,
                null,
                null,
                null);
    }
}
