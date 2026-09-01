package com.jiseong.homesense.batch.loader;

import java.util.LinkedHashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;

import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import com.jiseong.homesense.batch.parser.dto.TradeDraft;
import com.jiseong.homesense.complex.entity.Complex;
import com.jiseong.homesense.complex.repository.ComplexRepository;
import com.jiseong.homesense.region.entity.LegalDistrictCode;
import com.jiseong.homesense.region.repository.LegalDistrictCodeRepository;
import com.jiseong.homesense.trade.entity.Trade;
import com.jiseong.homesense.trade.repository.TradeRepository;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * TradeDataLoader가 나눈 청크 하나를 한 트랜잭션으로 처리한다. TradeDataLoader 안의 private 메서드가
 * 아니라 별도 빈으로 분리한 이유: Spring AOP 트랜잭션 프록시는 빈 경계를 넘는 호출에만 적용되고
 * 같은 인스턴스 안에서의 self-invocation은 프록시를 우회하므로, TradeDataLoader.loadBatch()가 청크마다
 * 실제로 별도 트랜잭션 커밋 경계를 갖게 하려면 이렇게 나눠야 한다.
 */
@Slf4j
@Component
@RequiredArgsConstructor
class TradeChunkLoader {

    private final TradeRepository tradeRepository;
    private final LegalDistrictCodeRepository legalDistrictCodeRepository;
    private final ComplexRepository complexRepository;
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
     * @return true면 신규 INSERT, false면 기존 행 UPDATE.
     */
    private boolean upsertOne(TradeDraft draft) {
        String dedupHash = dedupHashCalculator.calculate(draft);

        Optional<Trade> existing = tradeRepository.findByDedupHash(dedupHash);
        if (existing.isPresent()) {
            applyLateUpdate(existing.get(), draft);
            return false;
        }

        try {
            tradeRepository.saveAndFlush(buildNewTrade(draft, dedupHash));
            return true;
        } catch (DataIntegrityViolationException raceCondition) {
            // dedup_hash UNIQUE 충돌: findByDedupHash 조회 이후 이 실행이 INSERT하기 전에 다른 배치
            // 실행(겹치는 스케줄 등)이 먼저 같은 dedup_hash로 삽입을 끝낸 동시성 race condition이다.
            // MariaDB는 이런 문장 단위 실패로 트랜잭션 전체를 중단시키지 않으므로(PostgreSQL과 달리)
            // 같은 청크 트랜잭션 안에서 그대로 UPDATE로 낙관적 재시도를 1회 수행한다 — 이 재시도까지
            // 실패하면 예외가 그대로 전파되어 loadChunk()가 잡아 해당 건만 스킵한다.
            Trade winner = tradeRepository.findByDedupHash(dedupHash).orElseThrow(() -> raceCondition);
            applyLateUpdate(winner, draft);
            tradeRepository.saveAndFlush(winner);
            return false;
        }
    }

    private void applyLateUpdate(Trade trade, TradeDraft draft) {
        trade.applyLateUpdate(draft.cancelYn(), draft.cancelDate(), draft.registrationDate(), draft.aptDong());
    }

    private Trade buildNewTrade(TradeDraft draft, String dedupHash) {
        return Trade.builder()
                .housingType(draft.housingType())
                .dealCategory(draft.dealCategory())
                .rentType(draft.rentType())
                .datasetId(draft.datasetId())
                .sggCd(draft.sggCd())
                .legalDistrictCode(legalDistrictCodeReference(draft.legalDongCd()))
                .umdNm(draft.umdNm())
                .complex(complexReference(draft.complexId()))
                .buildingName(draft.buildingName())
                .jibun(draft.jibun())
                .excluUseArea(draft.excluUseArea())
                .floor(draft.floor())
                .buildYear(draft.buildYear())
                .dealDate(draft.dealDate())
                .dealAmount(draft.dealAmount())
                .depositAmount(draft.depositAmount())
                .monthlyRentAmount(draft.monthlyRentAmount())
                .aptDong(draft.aptDong())
                .dealingType(draft.dealingType())
                .agentSggNm(draft.agentSggNm())
                .registrationDate(draft.registrationDate())
                .sellerType(draft.sellerType())
                .buyerType(draft.buyerType())
                .landLeaseYn(draft.landLeaseYn())
                .cancelYn(draft.cancelYn())
                .cancelDate(draft.cancelDate())
                .matchMethod(draft.matchMethod())
                .matchConfidence(draft.matchConfidence())
                .dedupHash(dedupHash)
                .build();
    }

    private LegalDistrictCode legalDistrictCodeReference(String legalDongCd) {
        return legalDongCd == null ? null : legalDistrictCodeRepository.getReferenceById(legalDongCd);
    }

    private Complex complexReference(Long complexId) {
        return complexId == null ? null : complexRepository.getReferenceById(complexId);
    }
}
