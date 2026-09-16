package com.jiseong.homesense.batch.matcher;

import java.time.LocalDateTime;
import java.util.function.Function;

import org.springframework.stereotype.Component;

import com.jiseong.homesense.batch.matcher.TradeRematchBatchProcessor.BatchOutcome;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * BAT-MAT-02(ComplexMasterMatcher) 로직이 수정된 뒤(버그 A/B/C 등), 그 수정 전에 이미 매칭된 trade
 * 행에 새 매칭 결과를 반영하는 일회성 재매칭 경로다.
 *
 * <p>{@code TradeRepository.upsert()}는 재수집 시 매칭 필드(complex_id/match_method/
 * match_confidence)를 의도적으로 보존한다(그 javadoc, "재매칭은 이 upsert의 책임이 아니다") — 그래서
 * 매처 로직이 개선돼도 이미 적재된 stale 행은 재수집만으로는 절대 갱신되지 않는다(실 DB로 확인: 버그
 * A/B 수정 전 09-14 수집분 21,230건이 이후 여러 차례 재수집이 일어난 뒤에도 그대로 미매칭 상태로
 * 남아있었다). 이 클래스가 그 간극을 메운다.
 *
 * <p><b>{@link #rematchUnmatched()}(1차 패스)만으로는 충분하지 않다.</b> complex_id IS NULL 행만
 * 대상으로 삼으면, 버그 A/B 수정 전 지번 비교가 막혀 있던 시절 "그나마 비슷한 단지"로 SIMILAR
 * 배정됐던 행(complex_id가 이미 채워져 있음)은 대상에서 빠진다 — 그 SIMILAR 배정이 실은 오배정이었고,
 * 버그 C까지 고친 지금 다시 돌리면 지번이 정확히 일치하는 다른 단지로 EXACT 재배정돼야 하는 경우가
 * 있을 수 있다. {@link #rematchUpdatedBefore(LocalDateTime)}(2차 패스)가 이 간극을 메운다 — complex_id
 * 유무와 무관하게 특정 시각 이전에 매칭(또는 최초 적재)된 행 전부를 다시 돌린다.
 *
 * <p>실제 배치 단위(조회~매칭~갱신) 처리는 이 클래스가 직접 하지 않고 {@link TradeRematchBatchProcessor}
 * 에 위임한다 — 이 클래스(오케스트레이터)는 커서만 들고 반복 호출할 뿐 트랜잭션 경계를 갖지 않는다.
 * 배치 하나를 통째로 트랜잭션 하나로 묶었던 최초 구현(전체 순회를 이 클래스의 @Transactional 메서드
 * 하나로 감쌈)은 수만 건 전체가 끝나야 단 한 번 커밋되는 초장기 트랜잭션이 돼, 중간에 실패하면 그동안의
 * 작업이 전부 롤백되고 그 전까지는 외부에서 진행 상황을 전혀 관측할 수 없었다(실측: 1시간 반을 돌렸는데
 * DB의 MAX(updated_at)이 전혀 갱신되지 않았다) — {@link TradeRematchBatchProcessor}로 배치(500건) 단위
 * 커밋으로 쪼개 이 문제를 해결했다.
 *
 * <p>매일 도는 BAT-SCH-01 파이프라인에는 배선하지 않는다 — 매처 로직이 바뀔 때만 수동으로 트리거하는
 * 유지보수용 경로다({@code rematch} 프로필로만 활성화되는 {@code TradeRematchCommandLineRunner} 참고).
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class TradeRematchRunner {

    private final TradeRematchBatchProcessor batchProcessor;

    /**
     * 재매칭 결과 요약. {@code unchanged}는 매처를 다시 돌려도 이전과 완전히 같은 결과(같은 complexId,
     * 같은 matchMethod — 둘 다 매칭 실패인 경우도 포함)가 나온 건수, {@code changed}는 결과가 달라져
     * {@code applyRematch()}로 실제 갱신이 일어난 건수다("새로 매칭됨"/"다른 단지로 재배정됨"/"매칭이
     * 풀림" 세 경우를 전부 포함 — 세 경우를 구분해 보고 싶으면 로그의 개별 재배정 라인을 참고하라).
     */
    public record RematchSummary(int unchanged, int changed) {
    }

    /**
     * 1차 패스: legal_dong_cd는 있지만(BAT-MAT-01 성공) complex_id는 없는(BAT-MAT-02 실패) 행만
     * 대상으로 삼는다.
     */
    public RematchSummary rematchUnmatched() {
        RematchSummary summary = runToCompletion(batchProcessor::processUnmatchedBatch);
        log.info("BAT-MAT-02 재매칭(1차, complex_id IS NULL) 완료: unchanged={}, changed={}",
                summary.unchanged(), summary.changed());
        return summary;
    }

    /**
     * 2차 패스: complex_id 유무와 무관하게 {@code cutoff} 이전에 갱신된(=매처가 지금 로직으로 한 번도
     * 다시 돌지 않은) 행 전부를 대상으로 삼는다 — 버그 A/B 시절 SIMILAR로 오배정된 행이 여기서 잡힌다.
     * {@code applyRematch()}는 결과가 이전과 같으면 아예 호출하지 않으므로(멱등), 이미 1차 패스가 고친
     * 행이 다시 포함돼도 안전하다 — 그 행들은 unchanged로만 집계된다.
     */
    public RematchSummary rematchUpdatedBefore(LocalDateTime cutoff) {
        RematchSummary summary = runToCompletion(cursor -> batchProcessor.processUpdatedBeforeBatch(cutoff, cursor));
        log.info("BAT-MAT-02 재매칭(2차, updated_at<{}) 완료: unchanged={}, changed={}",
                cutoff, summary.unchanged(), summary.changed());
        return summary;
    }

    /**
     * dedup_hash 전수 복구 — 매칭 결과(complex_id/match_method/match_confidence)는 전혀 바꾸지 않고,
     * 각 행의 현재 매칭 상태를 기준으로 dedup_hash만 다시 계산해 저장된 값과 다르면 바로잡는다.
     * {@link #rematchUnmatched()}/{@link #rematchUpdatedBefore(LocalDateTime)}가 이 메서드보다 먼저
     * 존재했을 때는 {@code applyRematch()}가 dedup_hash를 갱신하지 않아, 그 두 패스가 이미 만들어낸
     * "changed" 행들의 dedup_hash가 새 complex_id와 맞지 않는 채로 DB에 남아있었다(Codex 코드리뷰 P1
     * 지적 — {@link TradeRematchBatchProcessor#applyChange} 수정으로 이후 재매칭부터는 발생하지 않지만,
     * 그 수정 전에 이미 만들어진 기존 stale 행은 남아있으므로 한 번은 이 메서드로 정리해야 한다).
     */
    public RematchSummary repairDedupHashes() {
        RematchSummary summary = runToCompletion(batchProcessor::repairDedupHashBatch);
        log.info("BAT-MAT-02 dedup_hash 전수 복구 완료: unchanged={}, changed={}",
                summary.unchanged(), summary.changed());
        return summary;
    }

    private RematchSummary runToCompletion(Function<Long, BatchOutcome> nextBatch) {
        int unchanged = 0;
        int changed = 0;
        Long cursor = 0L;

        BatchOutcome outcome = nextBatch.apply(cursor);
        while (outcome.hasMore()) {
            unchanged += outcome.unchanged();
            changed += outcome.changed();
            cursor = outcome.lastTradeId();
            outcome = nextBatch.apply(cursor);
        }

        return new RematchSummary(unchanged, changed);
    }
}
