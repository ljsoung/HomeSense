package com.jiseong.homesense.batch.errorhandler;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;

import org.junit.jupiter.api.Test;

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
        manager.processRetryQueue(req -> true, exhausted::add);

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
            return false;
        }, exhausted::add);

        // backoffSchedule 길이(3)만큼만 재시도하고 그 이상은 소진 처리된다.
        assertThat(attemptCount).hasSize(3);
        assertThat(exhausted).containsExactly(failing);
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
            return true;
        }, req -> {
            throw new AssertionError("소진 콜백은 호출되지 않아야 한다: " + req);
        });

        assertThat(succeeded).containsExactlyInAnyOrder(first, second);
    }

    @Test
    void 큐가_비어있으면_아무_콜백도_호출되지_않는다() {
        manager.processRetryQueue(
                req -> { throw new AssertionError("호출되지 않아야 한다"); },
                req -> { throw new AssertionError("호출되지 않아야 한다"); });
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
            return false;
        }, exhausted::add);

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
                exhausted::add);

        assertThat(exhausted).hasSize(1);
        assertThat(cappedManager.sleptDurations).isEmpty();
    }
}
