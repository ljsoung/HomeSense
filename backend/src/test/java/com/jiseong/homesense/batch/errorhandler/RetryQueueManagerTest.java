package com.jiseong.homesense.batch.errorhandler;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;

import org.junit.jupiter.api.Test;

import com.jiseong.homesense.batch.errorhandler.RetryQueueManager.PendingRetry;
import com.jiseong.homesense.common.config.RetryQueueProperties;
import com.jiseong.homesense.trade.entity.DealCategory;
import com.jiseong.homesense.trade.entity.HousingType;

class RetryQueueManagerTest {

    // 백오프를 0분으로 둬 테스트가 분 단위로 대기하지 않게 한다 — 스케줄 값 자체(1,5,30)는
    // application.properties의 homesense.batch.retry.backoff-minutes로만 관리된다.
    // maxTotalWaitMinutes는 여기서 검증하는 시나리오와 무관하므로 넉넉히 잡아둔다(대기 예산
    // 상한 자체는 아래 별도 테스트에서 sleep()을 오버라이드해 검증한다).
    private final RetryQueueManager manager =
            new RetryQueueManager(new RetryQueueProperties(List.of(0L, 0L, 0L), 999_999L));

    private static CollectRequest request(String sggCd) {
        return new CollectRequest(HousingType.APT, DealCategory.SALE, sggCd, "202401");
    }

    @Test
    void 큐에_적재된_요청이_첫_재시도에_성공하면_소진_콜백은_호출되지_않는다() {
        manager.enqueueRetry(request("11680"));

        List<CollectRequest> exhausted = new ArrayList<>();
        manager.processRetryQueue(req -> RetryOutcome.success(), (req, detail) -> exhausted.add(req));

        assertThat(exhausted).isEmpty();
    }

    @Test
    void 재시도가_계속_실패하면_backoffSchedule_길이만큼만_재시도하고_소진_콜백을_호출한다() {
        CollectRequest failing = request("11680");
        manager.enqueueRetry(failing);

        List<Integer> attemptCount = new ArrayList<>();
        List<CollectRequest> exhausted = new ArrayList<>();
        manager.processRetryQueue(req -> {
            attemptCount.add(1);
            return RetryOutcome.stillFailing(RetryFailureDetail.EMPTY);
        }, (req, detail) -> exhausted.add(req));

        // backoffSchedule 길이(3)만큼만 재시도하고 그 이상은 소진 처리된다.
        assertThat(attemptCount).hasSize(3);
        assertThat(exhausted).containsExactly(failing);
    }

    @Test
    void 소진_콜백은_마지막으로_관측된_실제_API_오류를_그대로_전달받는다() {
        // 매번 다른 resultCode로 실패한다고 가정하면, 소진 시점엔 가장 마지막(3번째) 시도의
        // 오류만 남아 있어야 한다 — 첫 시도의 오류로 덮어써지거나 뒤섞이면 안 된다.
        CollectRequest failing = request("11680");
        manager.enqueueRetry(failing);

        List<String> resultCodes = List.of("22", "01", "04");
        List<Integer> attemptCount = new ArrayList<>();
        List<RetryFailureDetail> exhaustedDetails = new ArrayList<>();
        manager.processRetryQueue(req -> {
            String resultCode = resultCodes.get(attemptCount.size());
            attemptCount.add(1);
            return RetryOutcome.stillFailing(new RetryFailureDetail("15126468", resultCode, "message-" + resultCode));
        }, (req, detail) -> exhaustedDetails.add(detail));

        assertThat(exhaustedDetails).hasSize(1);
        assertThat(exhaustedDetails.get(0).datasetId()).isEqualTo("15126468");
        assertThat(exhaustedDetails.get(0).resultCode()).isEqualTo("04");
        assertThat(exhaustedDetails.get(0).message()).isEqualTo("message-04");
    }

    @Test
    void 여러_요청이_적재되면_전부_독립적으로_처리된다() {
        CollectRequest first = request("11680");
        CollectRequest second = request("11740");
        manager.enqueueRetry(first);
        manager.enqueueRetry(second);

        List<CollectRequest> succeeded = new ArrayList<>();
        manager.processRetryQueue(req -> {
            succeeded.add(req);
            return RetryOutcome.success();
        }, (req, detail) -> {
            throw new AssertionError("소진 콜백은 호출되지 않아야 한다: " + req);
        });

        assertThat(succeeded).containsExactlyInAnyOrder(first, second);
    }

    @Test
    void clear는_큐에_남은_모든_요청을_반환하고_큐를_비운다() {
        CollectRequest first = request("11680");
        CollectRequest second = request("11740");
        manager.enqueueRetry(first);
        manager.enqueueRetry(second);

        List<PendingRetry> pending = manager.clear();

        assertThat(pending).extracting(PendingRetry::request).containsExactlyInAnyOrder(first, second);
        assertThat(pending).extracting(PendingRetry::lastFailure)
                .containsOnly(RetryFailureDetail.EMPTY);

        // 비워진 뒤엔 processRetryQueue를 호출해도 아무 콜백도 발생하지 않아야 한다.
        manager.processRetryQueue(
                req -> { throw new AssertionError("clear() 이후엔 재시도가 시도되면 안 된다"); },
                (req, detail) -> { throw new AssertionError("clear() 이후엔 소진 콜백도 호출되면 안 된다"); });
    }

    @Test
    void clear는_이미_한_번_이상_재시도돼_관측된_실패_정보도_함께_반환한다() {
        // A는 먼저 한 번 재시도돼 실패 정보(detailA)가 큐 엔트리에 보존된 채 재적재되고,
        // 이어서 B를 재시도하는 도중 예외(배치 조기 중단을 흉내)가 발생해 processRetryQueue가
        // 중단된다. 이때 아직 큐에 남아 있는 A의 lastFailure는 유실되지 않고 clear()로 그대로
        // 회수돼야 한다 — B는 poll()로 이미 큐에서 빠진 뒤 예외가 나서 큐에 남지 않는다(호출부가
        // 이미 별도로 실패 기록을 남긴 뒤 예외를 던지는 흐름과 대응된다).
        CollectRequest a = request("11680");
        CollectRequest b = request("11740");
        manager.enqueueRetry(a);
        manager.enqueueRetry(b);
        RetryFailureDetail detailA = new RetryFailureDetail("15126469", "22", "트래픽 초과");
        RuntimeException abort = new RuntimeException("시뮬레이션된 조기 중단");

        assertThatThrownBy(() -> manager.processRetryQueue(req -> {
            if (req.equals(a)) {
                return RetryOutcome.stillFailing(detailA);
            }
            throw abort;
        }, (req, detail) -> {
            throw new AssertionError("소진 콜백이 호출되면 안 된다: " + req);
        })).isSameAs(abort);

        List<PendingRetry> pending = manager.clear();

        assertThat(pending).containsExactly(new PendingRetry(a, detailA));
    }

    @Test
    void 큐가_비어있으면_아무_콜백도_호출되지_않는다() {
        manager.processRetryQueue(
                req -> { throw new AssertionError("호출되지 않아야 한다"); },
                (req, detail) -> { throw new AssertionError("호출되지 않아야 한다"); });
    }

    /**
     * maxTotalWaitMinutes 상한 검증. backoffMinutes(1,5,30)를 그대로 쓰되 sleep()을
     * 오버라이드해 실제로 분 단위로 기다리지 않고도 누적 대기 예산 회계를 검증한다.
     */
    private static class FakeSleepRetryQueueManager extends RetryQueueManager {
        private final List<Duration> sleptDurations = new ArrayList<>();

        FakeSleepRetryQueueManager(RetryQueueProperties properties) {
            super(properties);
        }

        @Override
        void sleep(Duration duration) {
            sleptDurations.add(duration);
        }
    }

    @Test
    void 누적_대기_시간이_예산을_넘으면_남은_큐는_대기_없이_즉시_소진_처리된다() {
        // backoffSchedule=[1,5,30]분, 예산=10분, 두 조합(A,B)이 매번 실패한다고 가정하면
        // 라운드로빈 순서는 A(1분,누적1)→B(1분,누적2)→A(5분,누적7)→B(5분 시도 시 누적12로
        // 예산 초과 → 대기 없이 즉시 소진)→A(30분 시도 시 역시 예산 초과 → 즉시 소진).
        FakeSleepRetryQueueManager cappedManager =
                new FakeSleepRetryQueueManager(new RetryQueueProperties(List.of(1L, 5L, 30L), 10L));
        CollectRequest a = request("11680");
        CollectRequest b = request("11740");
        cappedManager.enqueueRetry(a);
        cappedManager.enqueueRetry(b);

        List<CollectRequest> attempted = new ArrayList<>();
        List<CollectRequest> exhausted = new ArrayList<>();
        cappedManager.processRetryQueue(req -> {
            attempted.add(req);
            return RetryOutcome.stillFailing(RetryFailureDetail.EMPTY);
        }, (req, detail) -> exhausted.add(req));

        assertThat(attempted).containsExactly(a, b, a);
        assertThat(exhausted).containsExactly(b, a);
        assertThat(cappedManager.sleptDurations).containsExactly(
                Duration.ofMinutes(1), Duration.ofMinutes(1), Duration.ofMinutes(5));
    }

    @Test
    void 예산이_0이면_큐의_모든_요청이_대기_없이_즉시_소진_처리된다() {
        FakeSleepRetryQueueManager cappedManager =
                new FakeSleepRetryQueueManager(new RetryQueueProperties(List.of(1L, 5L, 30L), 0L));
        cappedManager.enqueueRetry(request("11680"));

        List<CollectRequest> exhausted = new ArrayList<>();
        cappedManager.processRetryQueue(
                req -> { throw new AssertionError("예산 초과 상태이므로 재시도가 시도되면 안 된다"); },
                (req, detail) -> exhausted.add(req));

        assertThat(exhausted).hasSize(1);
        assertThat(cappedManager.sleptDurations).isEmpty();
    }
}
