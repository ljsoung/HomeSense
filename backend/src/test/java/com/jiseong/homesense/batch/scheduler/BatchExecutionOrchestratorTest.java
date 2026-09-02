package com.jiseong.homesense.batch.scheduler;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.reset;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.YearMonth;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.web.client.RestClientException;

import com.jiseong.homesense.batch.collector.ApiCallThrottle;
import com.jiseong.homesense.batch.collector.ApiResponseXml;
import com.jiseong.homesense.batch.collector.DatasetPage;
import com.jiseong.homesense.batch.collector.DatasetRegistry;
import com.jiseong.homesense.batch.collector.OpenApiResponseException;
import com.jiseong.homesense.batch.collector.OpenApiResultCodeException;
import com.jiseong.homesense.batch.collector.RealEstateApiCollector;
import com.jiseong.homesense.batch.entity.BatchLog;
import com.jiseong.homesense.batch.errorhandler.CollectRequest;
import com.jiseong.homesense.batch.errorhandler.ErrorCodeJudgment;
import com.jiseong.homesense.batch.errorhandler.RetryQueueManager;
import com.jiseong.homesense.batch.repository.BatchLogRepository;
import com.jiseong.homesense.common.config.BatchSchedulerProperties;
import com.jiseong.homesense.common.config.RetryQueueProperties;
import com.jiseong.homesense.region.repository.LegalDistrictCodeRepository;
import com.jiseong.homesense.trade.entity.DealCategory;
import com.jiseong.homesense.trade.entity.HousingType;

@ExtendWith(MockitoExtension.class)
class BatchExecutionOrchestratorTest {

    private static final String SGG_CD = "11680";
    private static final YearMonth TARGET_MONTH = YearMonth.of(2024, 3);

    @Mock
    private LegalDistrictCodeRepository legalDistrictCodeRepository;

    @Mock
    private RealEstateApiCollector collector;

    @Mock
    private BatchLogRepository batchLogRepository;

    @Mock
    private ApplicationEventPublisher eventPublisher;

    // 실제 조합(housingType×dealCategory)마다 등록된 데이터셋을 그대로 알고 있어야 하는 테스트(구조적
    // 오류의 대표 데이터셋 귀속)가 있어 목이 아니라 실제 구현을 쓴다 — RealEstateApiCollectorTest와 동일 관례.
    private final DatasetRegistry datasetRegistry = new DatasetRegistry();

    private BatchExecutionOrchestrator orchestrator;
    private RetryQueueManager retryQueueManager;

    @BeforeEach
    void setUp() {
        lenient().when(legalDistrictCodeRepository.findDistinctActiveSggCd()).thenReturn(List.of(SGG_CD));
        BatchSchedulerProperties properties = new BatchSchedulerProperties(List.of(HousingType.APT));
        // 백오프를 0분으로 둬 재시도 큐 처리가 테스트를 분 단위로 지연시키지 않게 한다 —
        // 실제 백오프 스케줄(1,5,30분)은 RetryQueueManagerTest에서 별도로 검증한다.
        retryQueueManager = new RetryQueueManager(new RetryQueueProperties(List.of(0L, 0L, 0L), 999_999L));
        orchestrator = new BatchExecutionOrchestrator(legalDistrictCodeRepository, collector, datasetRegistry,
                batchLogRepository, properties, eventPublisher, new ApiCallThrottle(), retryQueueManager);
    }

    private static ApiResponseXml success(String datasetId) {
        return new ApiResponseXml(List.of(new DatasetPage(datasetId, "<response/>")));
    }

    @Test
    void throttle는_연속_호출_간_최소_지연을_보장한다() {
        long start = System.currentTimeMillis();
        orchestrator.throttle();
        orchestrator.throttle();
        long elapsed = System.currentTimeMillis() - start;

        assertThat(elapsed).isGreaterThanOrEqualTo(40);
    }

    @Test
    void 조합마다_collect를_호출하고_성공하면_배치로그를_남기고_완료_이벤트를_발행한다() {
        // sgg 1개 × 계약월 2개(전월+당월) × housingType 1개(APT) × dealCategory 2개 = 4개 조합
        when(collector.collect(any(), any(), any(), any())).thenReturn(success("15126469"));

        orchestrator.orchestrate(TARGET_MONTH);

        verify(collector, times(4)).collect(any(), any(), any(), any());
        verify(batchLogRepository, times(4)).save(any(BatchLog.class));
        ArgumentCaptor<TradeCollectionCompletedEvent> eventCaptor =
                ArgumentCaptor.forClass(TradeCollectionCompletedEvent.class);
        verify(eventPublisher).publishEvent(eventCaptor.capture());
        assertThat(eventCaptor.getValue().targetMonth()).isEqualTo(TARGET_MONTH);
    }

    @Test
    void ABORT_COMBINATION이면_해당_조합만_실패로_기록하고_나머지_조합은_계속_진행한다() {
        OpenApiResultCodeException abortCombination =
                new OpenApiResultCodeException("15126469", "10", ErrorCodeJudgment.ABORT_COMBINATION);
        when(collector.collect(any(), any(), any(), any()))
                .thenThrow(abortCombination)
                .thenReturn(success("15126469"));

        orchestrator.orchestrate(TARGET_MONTH);

        verify(collector, times(4)).collect(any(), any(), any(), any());
        verify(batchLogRepository, times(4)).save(any(BatchLog.class));
        verify(eventPublisher).publishEvent(any(TradeCollectionCompletedEvent.class));
    }

    @Test
    void RETRY_판정이면_재시도_후_성공하면_정상_처리된다() {
        OpenApiResultCodeException retry =
                new OpenApiResultCodeException("15126469", "22", ErrorCodeJudgment.RETRY);
        when(collector.collect(any(), any(), any(), any()))
                .thenThrow(retry)
                .thenThrow(retry)
                .thenReturn(success("15126469"));

        orchestrator.orchestrate(TARGET_MONTH);

        // 4개 조합 모두 최초 시도 1번씩(4번) 거친 뒤, RETRY로 큐에 적재된 조합만 전체 순회가
        // 끝난 뒤 재시도 큐에서 다시 시도된다. mock 스텁이 앞 2번 호출만 실패라 두 조합이 큐에
        // 적재되고, 큐 처리 시점엔 이미 성공 스텁만 남아 각 1번씩(2번) 재시도로 성공한다 → 총 6번.
        verify(collector, times(6)).collect(any(), any(), any(), any());
        verify(batchLogRepository, times(4)).save(any(BatchLog.class));
        verify(eventPublisher).publishEvent(any(TradeCollectionCompletedEvent.class));
    }

    @Test
    void RETRY_판정된_조합은_블로킹_재시도_없이_전체_순회가_끝난_뒤에야_재시도된다() {
        // BAT-ERR-01 핵심 동작: RETRY는 그 자리에서 블로킹 재시도하지 않고 큐에 적재만 해두고
        // 다음 조합으로 즉시 넘어간다 — 4개 조합의 최초 시도(1~4번째 호출)가 모두 끝난 뒤에야
        // 재시도(5번째 호출)가 일어나야 한다.
        OpenApiResultCodeException retry =
                new OpenApiResultCodeException("15126469", "22", ErrorCodeJudgment.RETRY);
        AtomicInteger callCount = new AtomicInteger();
        List<Integer> retryCallIndexes = new ArrayList<>();
        when(collector.collect(any(), any(), any(), any())).thenAnswer(invocation -> {
            int index = callCount.incrementAndGet();
            if (index == 1) {
                throw retry;
            }
            if (index == 5) {
                retryCallIndexes.add(index);
            }
            return success("15126469");
        });

        orchestrator.orchestrate(TARGET_MONTH);

        assertThat(callCount.get()).isEqualTo(5);
        assertThat(retryCallIndexes).containsExactly(5);
    }

    @Test
    void RETRY가_최대_재시도_횟수를_넘기면_해당_조합을_실패로_기록하고_계속_진행한다() {
        OpenApiResultCodeException retry =
                new OpenApiResultCodeException("15126469", "22", ErrorCodeJudgment.RETRY);
        when(collector.collect(any(), any(), any(), any())).thenThrow(retry);

        orchestrator.orchestrate(TARGET_MONTH);

        // 조합마다 최초 시도 1회(4번) + 전체 순회가 끝난 뒤 재시도 큐에서 조합당 3회씩(12번) → 16번
        verify(collector, times(16)).collect(any(), any(), any(), any());
        verify(batchLogRepository, times(4)).save(any(BatchLog.class));
        verify(eventPublisher).publishEvent(any(TradeCollectionCompletedEvent.class));
    }

    @Test
    void RETRY가_소진되면_대표_데이터셋이_아닌_마지막으로_관측된_실제_API_오류를_기록한다() {
        // APT+SALE 조합은 데이터셋이 2개다(대표: 15126469, 상세: 15126468). 실패가 대표가 아닌
        // 상세 데이터셋에서 코드 22로 계속 발생했다면, 소진 시에도 그 사실(15126468/22)이 그대로
        // 기록돼야 한다 — 대표 데이터셋(15126469)과 "N/A"로 뭉뚱그려지면 어떤 데이터셋의 어떤
        // 오류로 실패했는지 알 수 없게 된다.
        OpenApiResultCodeException retryFromDetailDataset =
                new OpenApiResultCodeException("15126468", "22", ErrorCodeJudgment.RETRY);
        when(collector.collect(any(), eq(DealCategory.SALE), any(), any())).thenThrow(retryFromDetailDataset);
        when(collector.collect(any(), eq(DealCategory.RENT), any(), any())).thenReturn(success("15126474"));

        orchestrator.orchestrate(TARGET_MONTH);

        ArgumentCaptor<BatchLog> batchLogCaptor = ArgumentCaptor.forClass(BatchLog.class);
        verify(batchLogRepository, atLeastOnce()).save(batchLogCaptor.capture());
        assertThat(batchLogCaptor.getAllValues())
                .filteredOn(log -> "15126468".equals(log.getDatasetId()))
                .isNotEmpty()
                .allSatisfy(log -> assertThat(log.getResultCode()).isEqualTo("22"));
    }

    @Test
    void ABORT_BATCH가_연속으로_임계치만큼_발생하면_배치_전체를_조기_중단한다() {
        OpenApiResultCodeException authError =
                new OpenApiResultCodeException("15126469", "30", ErrorCodeJudgment.ABORT_BATCH);
        when(collector.collect(any(), any(), any(), any())).thenThrow(authError);

        orchestrator.orchestrate(TARGET_MONTH);

        // 임계치(3회) 도달 즉시 중단 — 4개 조합 중 3개까지만 처리되고 완료 이벤트는 발행되지 않는다.
        verify(collector, times(3)).collect(any(), any(), any(), any());
        verify(batchLogRepository, times(3)).save(any(BatchLog.class));
        verify(eventPublisher, never()).publishEvent(any());
    }

    @Test
    void 배치가_조기_중단되면_그때까지_쌓인_재시도_큐도_함께_비워져_다음_사이클과_섞이지_않는다() {
        // RetryQueueManager는 싱글턴이라 orchestrate() 호출 경계를 넘어 큐가 유지된다. 1번째
        // 조합이 RETRY로 큐에 적재된 직후 이어지는 3개 조합이 연속 ABORT_BATCH(30)를 맞아
        // 임계치에 도달해 배치가 조기 중단되면, processRetryQueue()엔 아예 도달하지 못한다 —
        // 이때 큐를 비우지 않으면 이 RETRY 항목이 다음 배치 사이클의 큐에 그대로 남는다.
        OpenApiResultCodeException retry =
                new OpenApiResultCodeException("15126469", "22", ErrorCodeJudgment.RETRY);
        OpenApiResultCodeException authError =
                new OpenApiResultCodeException("15126469", "30", ErrorCodeJudgment.ABORT_BATCH);
        when(collector.collect(any(), any(), any(), any()))
                .thenThrow(retry)
                .thenThrow(authError)
                .thenThrow(authError)
                .thenThrow(authError);

        orchestrator.orchestrate(TARGET_MONTH);

        verify(collector, times(4)).collect(any(), any(), any(), any());
        // RETRY로 큐에 적재됐던 combo1이 조기 중단으로 비워지며 실패로 기록되어 4건(ABORT_BATCH
        // 3건 + 비워진 RETRY 1건).
        verify(batchLogRepository, times(4)).save(any(BatchLog.class));
        verify(eventPublisher, never()).publishEvent(any());

        // 다음 배치 사이클: 이번엔 전부 성공한다고 가정한다. 이전 사이클에서 큐가 비워지지
        // 않았다면, 이번 4개 조합 처리 후 큐 처리 단계에서 stale 항목이 추가로 한 번 더
        // collect()를 호출하게 되어 총 5번이 된다.
        reset(collector);
        when(collector.collect(any(), any(), any(), any())).thenReturn(success("15126469"));

        orchestrator.orchestrate(TARGET_MONTH);

        verify(collector, times(4)).collect(any(), any(), any(), any());
        verify(eventPublisher).publishEvent(any(TradeCollectionCompletedEvent.class));
    }

    @Test
    void 구조적_응답_오류면_대표_데이터셋으로_귀속시켜_실패로_기록하고_계속_진행한다() {
        when(collector.collect(any(), any(), any(), any()))
                .thenThrow(new OpenApiResponseException("응답 구조를 읽을 수 없다"));

        orchestrator.orchestrate(TARGET_MONTH);

        verify(collector, times(4)).collect(any(), any(), any(), any());
        ArgumentCaptor<BatchLog> batchLogCaptor = ArgumentCaptor.forClass(BatchLog.class);
        verify(batchLogRepository, times(4)).save(batchLogCaptor.capture());
        assertThat(batchLogCaptor.getAllValues())
                .allSatisfy(batchLog -> assertThat(batchLog.getResultCode()).isEqualTo("N/A"));
        verify(eventPublisher).publishEvent(any(TradeCollectionCompletedEvent.class));
    }

    @Test
    void 전송_계층_오류가_나면_재시도_후_성공하면_정상_처리된다() {
        // 회귀 테스트: data.go.kr의 HTTP 오류(429/5xx)·커넥션 타임아웃 등은 resultCode 판정 이전에
        // RestClient가 던지는 예외라 OpenApiResultCodeException/OpenApiResponseException 어느 쪽도
        // 아니었다 — 이걸 못 잡으면 하루치 배치 전체가 중단됐다.
        RestClientException transportError = new RestClientException("connection reset");
        when(collector.collect(any(), any(), any(), any()))
                .thenThrow(transportError)
                .thenThrow(transportError)
                .thenReturn(success("15126469"));

        orchestrator.orchestrate(TARGET_MONTH);

        // RETRY 케이스와 동일한 이유로 6번(최초 4번 + 큐 적재된 2개 조합의 재시도 2번).
        verify(collector, times(6)).collect(any(), any(), any(), any());
        verify(batchLogRepository, times(4)).save(any(BatchLog.class));
        verify(eventPublisher).publishEvent(any(TradeCollectionCompletedEvent.class));
    }

    @Test
    void 전송_계층_오류가_재시도를_넘기면_해당_조합만_실패로_기록하고_배치_전체는_계속_진행한다() {
        RestClientException transportError = new RestClientException("connection reset");
        when(collector.collect(any(), any(), any(), any())).thenThrow(transportError);

        orchestrator.orchestrate(TARGET_MONTH);

        // 조합마다 최초 시도 1회(4번) + 전체 순회가 끝난 뒤 재시도 큐에서 조합당 3회씩(12번) → 16번
        verify(collector, times(16)).collect(any(), any(), any(), any());
        ArgumentCaptor<BatchLog> batchLogCaptor = ArgumentCaptor.forClass(BatchLog.class);
        verify(batchLogRepository, times(4)).save(batchLogCaptor.capture());
        assertThat(batchLogCaptor.getAllValues())
                .allSatisfy(batchLog -> assertThat(batchLog.getResultCode()).isEqualTo("N/A"));
        verify(eventPublisher).publishEvent(any(TradeCollectionCompletedEvent.class));
    }

    @Test
    void orchestrate_시작_시점에도_이전에_남아있던_재시도_큐를_비운다() {
        // CriticalBatchException 외의 원인(다른 예외, 프로세스 재기동 등)으로 이전 실행이 큐를
        // 못 비운 채 끝났을 가능성에 대비한 이중 방어선 — orchestrate() 시작 시점에도 큐를 비워
        // "새 실행은 항상 빈 큐로 시작한다"는 불변식을 보장해야 한다.
        CollectRequest staleFromPreviousRun =
                new CollectRequest(HousingType.APT, DealCategory.SALE, "99999", "202401");
        retryQueueManager.enqueueRetry(staleFromPreviousRun);

        when(collector.collect(any(), any(), any(), any())).thenReturn(success("15126469"));

        orchestrator.orchestrate(TARGET_MONTH);

        // stale 항목이 이번 실행의 재시도 큐 처리 단계에서 함께 재시도되지 않고(collect 호출은
        // 이번 실행 몫인 4번뿐), 조기 소진 처리로 실패 기록만 남긴 채(4개 조합 성공 로그 4건 +
        // stale 항목 실패 로그 1건 = 5건) 정상적으로 완료 이벤트가 발행된다.
        verify(collector, times(4)).collect(any(), any(), any(), any());
        verify(batchLogRepository, times(5)).save(any(BatchLog.class));
        verify(eventPublisher).publishEvent(any(TradeCollectionCompletedEvent.class));
    }
}
