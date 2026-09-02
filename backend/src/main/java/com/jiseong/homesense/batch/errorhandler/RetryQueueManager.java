package com.jiseong.homesense.batch.errorhandler;

import java.time.Duration;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.List;
import java.util.function.BiConsumer;
import java.util.function.Function;
import java.util.stream.Collectors;

import org.springframework.stereotype.Component;

import com.jiseong.homesense.batch.collector.BatchInterruptedException;
import com.jiseong.homesense.common.config.RetryQueueProperties;

/**
 * BAT-ERR-01(2차 처리). ApiErrorCodeClassifier가 RETRY로 판정한 요청(및 resultCode 이전 단계의
 * 전송 계층 오류)을 큐에 적재해두고, 시군구×계약월×주택유형×거래유형 전체 조합 순회가 끝난 뒤
 * 지수 백오프(기본 1분→5분→30분)로 재시도한다. 조합 처리 도중 분 단위 백오프로 블로킹하면
 * 순회 자체가 지연되므로, 실패 요청을 큐에 모아뒀다가 순회가 끝난 뒤 한 번에 처리한다.
 *
 * <p>다만 processRetryQueue() 호출 자체는 여전히 동기 블로킹이다 — 순회 중간에 지연시키지 않을
 * 뿐, 호출 스레드(orchestrate())는 큐에 쌓인 항목 수만큼 백오프가 누적되는 동안 그대로 대기한다.
 * 이 누적 대기 시간이 무한정 커지지 않도록 {@link RetryQueueProperties#maxTotalWaitMinutes()}로
 * 상한을 두며, 상한을 넘어서면 남은 큐 전체를 더 이상 대기·재시도하지 않고 즉시 최종 실패로
 * 처리해 다음 배치 사이클로 이월시킨다.
 */
@Component
public class RetryQueueManager {

    private final List<Duration> backoffSchedule;
    private final Duration maxTotalWait;
    private final Deque<RetryQueueEntry> queue = new ArrayDeque<>();

    public RetryQueueManager(RetryQueueProperties properties) {
        this.backoffSchedule = properties.backoffMinutes().stream().map(Duration::ofMinutes).toList();
        this.maxTotalWait = Duration.ofMinutes(properties.maxTotalWaitMinutes());
    }

    public void enqueueRetry(CollectRequest failedRequest) {
        queue.add(new RetryQueueEntry(failedRequest, 0, RetryFailureDetail.EMPTY));
    }

    /**
     * 큐에 남아 있는 모든 요청을 비우고 반환한다. 이 매니저는 싱글턴 빈이라 큐가 orchestrate()
     * 호출 경계를 넘어 그대로 유지된다 — 배치가 CriticalBatchException으로 조기 중단돼
     * processRetryQueue()에 도달하지 못하면, 그때까지 쌓인 항목이 비워지지 않은 채 다음 배치
     * 사이클의 큐에 그대로 남아 이번 실행의 요청과 뒤섞인다(계약월 축이 이동해 이미 목표 범위를
     * 벗어난 dealYmd를 다시 수집하게 될 수도 있다). 조기 중단 시 호출부(orchestrate())가 이
     * 메서드로 큐를 비우고 반환값을 실패로 기록해야 한다. 각 항목이 중단 전에 이미 한 번 이상
     * 재시도돼 실제 API 오류를 관측했다면 그 정보(lastFailure)도 함께 반환한다.
     */
    public List<PendingRetry> clear() {
        List<PendingRetry> pending = queue.stream()
                .map(entry -> new PendingRetry(entry.request(), entry.lastFailure()))
                .collect(Collectors.toList());
        queue.clear();
        return pending;
    }

    /**
     * 큐를 순회하며 재시도한다. retryAttempt가 resolved=true를 반환하면(성공했거나 더 이상 재시도
     * 대상이 아니면) 큐에서 제거하고, false(여전히 RETRY 판정)면 그 시도에서 관측된
     * failureDetail을 엔트리에 보존한 채 다음 백오프 단계로 재적재한다. backoffSchedule을
     * 소진하면(최대 재시도 횟수 초과) onExhausted로 최종 실패를 알리고 큐에서 제거한다 — 이때
     * 마지막으로 관측된 failureDetail을 함께 전달해, batch_log가 "N/A" 대신 실제 API가 돌려준
     * datasetId·resultCode를 남길 수 있게 한다. 이 요청은 다음 배치 사이클(익일 재수집)로
     * 자연히 이월된다.
     *
     * <p>대기 예산(maxTotalWait)을 이미 소진했다면, 이번 항목이 아직 한 번도 재시도되지
     * 않았더라도 더 대기하지 않고 곧바로 onExhausted로 넘긴다(이땐 이전에 관측된 detail이
     * 있으면 그것을, 한 번도 시도되지 않았다면 EMPTY를 전달한다) — 대량 장애로 큐가 커질 때
     * orchestrate()가 몇 시간씩 블로킹되는 것을 막기 위한 안전장치다.
     *
     * <p>Deque를 FIFO로 poll-then-append하기 때문에 이 소진 처리는 라운드로빈으로 공정하게
     * 분산된다 — 어떤 엔트리든 자신의 백오프 단계 하나를 시도하고 나서야 큐 뒤로 재적재되므로,
     * 큐 앞쪽 소수가 3단계 백오프를 전부 소진하는 동안 뒤쪽 엔트리는 단 한 번도 시도되지 못하는
     * 상황(순수 FIFO drain)은 생기지 않는다. 다만 예산이 한 라운드 중간에 소진되면, 그 라운드에서
     * 아직 poll되지 않은 엔트리는 해당 단계 시도 기회 없이 바로 소진 처리되므로, 엔트리 간 실제
     * 시도 횟수 격차는 최대 한 단계까지 발생할 수 있다.
     */
    public void processRetryQueue(Function<CollectRequest, RetryOutcome> retryAttempt,
                                   BiConsumer<CollectRequest, RetryFailureDetail> onExhausted) {
        Duration elapsedWait = Duration.ZERO;
        while (!queue.isEmpty()) {
            RetryQueueEntry entry = queue.poll();
            Duration backoff = backoffSchedule.get(entry.attempt());
            if (elapsedWait.plus(backoff).compareTo(maxTotalWait) > 0) {
                onExhausted.accept(entry.request(), entry.lastFailure());
                continue;
            }
            sleep(backoff);
            elapsedWait = elapsedWait.plus(backoff);
            RetryOutcome outcome = retryAttempt.apply(entry.request());
            if (outcome.resolved()) {
                continue;
            }
            int nextAttempt = entry.attempt() + 1;
            if (nextAttempt >= backoffSchedule.size()) {
                onExhausted.accept(entry.request(), outcome.failureDetail());
            } else {
                queue.add(new RetryQueueEntry(entry.request(), nextAttempt, outcome.failureDetail()));
            }
        }
    }

    void sleep(Duration duration) {
        try {
            Thread.sleep(duration.toMillis());
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new BatchInterruptedException("재시도 큐 백오프 대기 중 인터럽트됨", e);
        }
    }

    private record RetryQueueEntry(CollectRequest request, int attempt, RetryFailureDetail lastFailure) {
    }

    public record PendingRetry(CollectRequest request, RetryFailureDetail lastFailure) {
    }
}
