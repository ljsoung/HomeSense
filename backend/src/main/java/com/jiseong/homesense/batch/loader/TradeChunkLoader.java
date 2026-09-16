package com.jiseong.homesense.batch.loader;

import java.time.LocalDateTime;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import com.jiseong.homesense.batch.parser.dto.TradeDraft;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * TradeDataLoader가 나눈 청크 하나를 한 트랜잭션으로 처리한다. TradeDataLoader 안의 private 메서드가
 * 아니라 별도 빈으로 분리한 이유: Spring AOP 트랜잭션 프록시는 빈 경계를 넘는 호출에만 적용되고
 * 같은 인스턴스 안에서의 self-invocation은 프록시를 우회하므로, TradeDataLoader.loadBatch()가 청크마다
 * 실제로 별도 트랜잭션 커밋 경계를 갖게 하려면 이렇게 나눠야 한다.
 *
 * <p>{@code TradeRepository#upsert}(원자적 {@code INSERT ... ON DUPLICATE KEY UPDATE})로 건 하나를
 * 그대로 적재한다 — "조회 → INSERT 실패 시 재조회 후 UPDATE 재시도" 방식(TradeInsertGateway로 INSERT만
 * REQUIRES_NEW 격리)은 MariaDB REPEATABLE READ 스냅샷 문제로 실 배치에서 처리 대상의 2.2%가 유실되는
 * 결함이 있어 삭제했다(TradeRepository#upsert javadoc 참고).
 *
 * <p><b>단, 그 upsert 호출 자체는 {@link TradeUpsertGateway}를 통해 청크 트랜잭션과 별도로 격리한다</b>
 * (Codex 코드리뷰 P1 지적, 2026-09-16) — UNIQUE 경쟁이 아닌 다른 제약 위반(오버사이즈 값, UNSIGNED
 * 음수, 잘못된 FK 등)이 한 건이라도 발생하면 JPA 스펙상 그 순간 트랜잭션이 rollback-only로 표시되고,
 * 아래 루프의 try/catch가 그 건만 스킵한 것처럼 보여도 loadChunk() 커밋 시점에
 * {@code UnexpectedRollbackException}이 터져 이 청크(최대 500건) 전체가 롤백된다 — 딱 한 건의 나쁜
 * 데이터가 청크 전체를 무효화하는 셈이다. {@link TradeUpsertGateway} javadoc에 근거를 자세히 남겼다.
 */
@Slf4j
@Component
@RequiredArgsConstructor
class TradeChunkLoader {

    private final TradeUpsertGateway tradeUpsertGateway;
    private final DedupHashCalculator dedupHashCalculator;

    /**
     * public인 이유: Spring 프록시 기반 @Transactional은 public 메서드에만 보장된 동작이다
     * (package-private에도 CGLIB이 기술적으로 advise할 수는 있지만, 스프링 공식 문서상 보장 대상이
     * 아니라 나중에 proxyTargetClass 설정이 바뀌거나 인터페이스가 추가돼 JDK 동적 프록시로 전환되면
     * 조용히 트랜잭션이 빠질 수 있다). 클래스 자체는 여전히 package-private이라 패키지 밖 노출과는 무관하다.
     */
    @Transactional
    public ChunkOutcome loadChunk(List<TradeDraft> drafts) {
        int inserted = 0;
        int updated = 0;
        int errors = 0;
        Set<Long> touchedComplexIds = new LinkedHashSet<>();
        Set<String> touchedLegalDongCds = new LinkedHashSet<>();

        for (TradeDraft draft : drafts) {
            try {
                boolean wasInsert = upsertOne(draft);
                if (wasInsert) {
                    inserted++;
                } else {
                    updated++;
                }
                if (draft.complexId() != null) {
                    touchedComplexIds.add(draft.complexId());
                }
                if (draft.legalDongCd() != null) {
                    touchedLegalDongCds.add(draft.legalDongCd());
                }
            } catch (RuntimeException e) {
                errors++;
                log.error("BAT-LOD-01 적재 실패, 해당 건만 스킵한다: dealDate={}, complexId={}, dedupHash 계산 대상 sggCd={}",
                        draft.dealDate(), draft.complexId(), draft.sggCd(), e);
            }
        }

        LoadResult result = new LoadResult(inserted + updated, errors, inserted, updated);
        return new ChunkOutcome(result, touchedComplexIds, touchedLegalDongCds);
    }

    /**
     * @return true면 신규 INSERT로 집계, false면 기존 행 UPDATE로 집계(참고용 — 근거는
     *         {@link TradeRepository#upsert} javadoc의 반환값 설명 참고).
     */
    private boolean upsertOne(TradeDraft draft) {
        String dedupHash = dedupHashCalculator.calculate(draft);
        int affectedRows = tradeUpsertGateway.upsert(
                draft.housingType().name(),
                draft.dealCategory().name(),
                draft.rentType() == null ? null : draft.rentType().name(),
                draft.datasetId(),
                draft.sggCd(),
                draft.legalDongCd(),
                draft.umdNm(),
                draft.complexId(),
                draft.buildingName(),
                draft.jibun(),
                draft.excluUseArea(),
                draft.floor(),
                draft.buildYear(),
                draft.dealDate(),
                draft.dealAmount(),
                draft.depositAmount(),
                draft.monthlyRentAmount(),
                draft.aptDong(),
                draft.dealingType(),
                draft.agentSggNm(),
                draft.registrationDate(),
                draft.sellerType(),
                draft.buyerType(),
                draft.landLeaseYn(),
                draft.cancelYn(),
                draft.cancelDate(),
                draft.matchMethod() == null ? null : draft.matchMethod().name(),
                draft.matchConfidence(),
                dedupHash,
                LocalDateTime.now());
        return affectedRows <= 1;
    }
}
