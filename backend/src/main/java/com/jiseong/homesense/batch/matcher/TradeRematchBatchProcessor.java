package com.jiseong.homesense.batch.matcher;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Objects;

import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import com.jiseong.homesense.batch.loader.DedupHashCalculator;
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
    private final DedupHashCalculator dedupHashCalculator;

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

    /**
     * dedup_hash 전수 복구용 배치 — 매칭을 다시 돌리지 않고(현재 저장된 complex_id/match_method/
     * match_confidence를 그대로 신뢰), 그 값 기준으로 dedup_hash만 다시 계산해 저장된 값과 다르면
     * {@link #reconcileHashCollision}·{@code applyRematch}로 바로잡는다. {@link #applyChange}와 달리
     * complex_id 변경 여부를 게이트로 삼지 않고 매번 재계산한다 — 이 메서드의 목적 자체가 "저장된
     * 해시가 지금 매칭 상태와 맞는지"를 전수 검증하는 것이라 게이트를 걸 이유가 없다.
     */
    @Transactional
    BatchOutcome repairDedupHashBatch(Long cursor) {
        List<Trade> batch = tradeRepository.findByTradeIdGreaterThanOrderByTradeIdAsc(cursor, PageRequest.of(0, PAGE_SIZE));
        if (batch.isEmpty()) {
            return BatchOutcome.empty();
        }

        int unchanged = 0;
        int changed = 0;
        for (Trade trade : batch) {
            Long complexId = trade.getComplex() == null ? null : trade.getComplex().getComplexId();
            String legalDongCd = trade.getLegalDistrictCode() == null
                    ? null : trade.getLegalDistrictCode().getLegalDongCd();
            TradeDraft draft = toDraft(trade)
                    .withMatch(legalDongCd, complexId, trade.getMatchMethod(), trade.getMatchConfidence());
            String expectedHash = dedupHashCalculator.calculate(draft);

            if (expectedHash.equals(trade.getDedupHash())) {
                unchanged++;
                continue;
            }

            changed++;
            if (reconcileHashCollision(trade.getTradeId(), expectedHash)) {
                log.info("BAT-MAT-02 dedup_hash 복구: tradeId={}는 이미 다른 행과 동일 식별자로 수렴해 삭제함",
                        trade.getTradeId());
                continue;
            }
            tradeRepository.applyRematch(trade.getTradeId(), complexId,
                    trade.getMatchMethod() == null ? null : trade.getMatchMethod().name(),
                    trade.getMatchConfidence(), expectedHash, LocalDateTime.now());
            log.info("BAT-MAT-02 dedup_hash 복구: tradeId={}, {} -> {}", trade.getTradeId(),
                    trade.getDedupHash(), expectedHash);
        }

        return new BatchOutcome(batch.get(batch.size() - 1).getTradeId(), unchanged, changed, true);
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
                changed++;
                applyChange(trade, previousComplexId, previousMatchMethod, result);
            }
        }

        return new BatchOutcome(batch.get(batch.size() - 1).getTradeId(), unchanged, changed, true);
    }

    /**
     * complex_id가 실제로 바뀌는 경우(null↔값, 값↔다른 값)에만 dedup_hash를 재계산해 함께 갱신한다 —
     * {@link DedupHashCalculator}의 식별자가 complex_id 유무·값에 따라 달라지기 때문이다(그 javadoc
     * 참고). complex_id만 갱신하고 dedup_hash를 그대로 두면, 다음 정상 수집(BAT-SCH-01)이 같은
     * 실거래를 다시 파싱할 때 새 complex_id 기준 해시를 계산해 이 행의 저장된(옛) 해시와 달라지고,
     * {@code upsert()}의 UNIQUE 매칭이 빗나가 같은 실거래가 두 행으로 중복 적재된다(Codex 코드리뷰
     * P1 지적).
     *
     * <p>재계산한 해시를 이미 다른 행이 쓰고 있으면({@link #reconcileHashCollision}) 두 행이 재매칭
     * 결과 사실상 같은 실거래로 수렴한 것이다 — 그 다른 행은 이미 정상 수집 경로로 올바른 정체성을
     * 얻은 행이므로 이 stale 행을 삭제해 중복을 해소하고, UNIQUE 제약을 그대로 위반하게 두지 않는다.
     */
    private void applyChange(Trade trade, Long previousComplexId, MatchMethod previousMatchMethod, MatchResult result) {
        Long tradeId = trade.getTradeId();
        String dedupHash = trade.getDedupHash();

        if (!Objects.equals(previousComplexId, result.complexId())) {
            String legalDongCd = trade.getLegalDistrictCode() == null
                    ? null : trade.getLegalDistrictCode().getLegalDongCd();
            TradeDraft draftForHash = toDraft(trade)
                    .withMatch(legalDongCd, result.complexId(), result.matchMethod(), result.matchConfidence());
            String candidateHash = dedupHashCalculator.calculate(draftForHash);

            if (!candidateHash.equals(dedupHash)) {
                if (reconcileHashCollision(tradeId, candidateHash)) {
                    log.info("BAT-MAT-02 재매칭: tradeId={}는 재계산된 dedup_hash를 이미 다른 행이 쓰고 있어 "
                            + "중복으로 판단해 삭제함", tradeId);
                    return;
                }
                dedupHash = candidateHash;
            }
        }

        tradeRepository.applyRematch(tradeId, result.complexId(),
                result.matchMethod() == null ? null : result.matchMethod().name(),
                result.matchConfidence(), dedupHash, LocalDateTime.now());
        log.info("BAT-MAT-02 재배정: tradeId={}, {}({}) -> {}({})", tradeId,
                previousComplexId, previousMatchMethod, result.complexId(), result.matchMethod());
    }

    /**
     * @return true면 targetHash를 이미 다른 행이 쓰고 있어 tradeId 행을 삭제했다(호출자는
     *         applyRematch를 더 이상 호출하면 안 된다). false면 충돌이 없어 그대로 진행하면 된다.
     */
    private boolean reconcileHashCollision(Long tradeId, String targetHash) {
        return tradeRepository.findByDedupHash(targetHash)
                .filter(target -> !target.getTradeId().equals(tradeId))
                .map(target -> {
                    tradeRepository.deleteById(tradeId);
                    return true;
                })
                .orElse(false);
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
