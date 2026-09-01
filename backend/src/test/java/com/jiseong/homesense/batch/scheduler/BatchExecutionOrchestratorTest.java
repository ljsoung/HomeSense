package com.jiseong.homesense.batch.scheduler;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.YearMonth;
import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.ApplicationEventPublisher;

import com.jiseong.homesense.batch.collector.ApiResponseXml;
import com.jiseong.homesense.batch.collector.DatasetPage;
import com.jiseong.homesense.batch.collector.DatasetRegistry;
import com.jiseong.homesense.batch.collector.OpenApiResponseException;
import com.jiseong.homesense.batch.collector.OpenApiResultCodeException;
import com.jiseong.homesense.batch.collector.RealEstateApiCollector;
import com.jiseong.homesense.batch.entity.BatchLog;
import com.jiseong.homesense.batch.errorhandler.ErrorCodeJudgment;
import com.jiseong.homesense.batch.repository.BatchLogRepository;
import com.jiseong.homesense.common.config.BatchSchedulerProperties;
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

    @BeforeEach
    void setUp() {
        lenient().when(legalDistrictCodeRepository.findDistinctActiveSggCd()).thenReturn(List.of(SGG_CD));
        BatchSchedulerProperties properties = new BatchSchedulerProperties(List.of(HousingType.APT));
        orchestrator = new BatchExecutionOrchestrator(
                legalDistrictCodeRepository, collector, datasetRegistry, batchLogRepository, properties, eventPublisher);
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

        // 첫 조합에서 재시도 2회 + 성공 1회 = 3번, 나머지 3개 조합은 1번씩 = 6번
        verify(collector, times(6)).collect(any(), any(), any(), any());
        verify(batchLogRepository, times(4)).save(any(BatchLog.class));
        verify(eventPublisher).publishEvent(any(TradeCollectionCompletedEvent.class));
    }

    @Test
    void RETRY가_최대_재시도_횟수를_넘기면_해당_조합을_실패로_기록하고_계속_진행한다() {
        OpenApiResultCodeException retry =
                new OpenApiResultCodeException("15126469", "22", ErrorCodeJudgment.RETRY);
        when(collector.collect(any(), any(), any(), any())).thenThrow(retry);

        orchestrator.orchestrate(TARGET_MONTH);

        // 조합마다 최초 시도 1회 + 재시도 3회 = 4번, 조합은 4개 → 16번
        verify(collector, times(16)).collect(any(), any(), any(), any());
        verify(batchLogRepository, times(4)).save(any(BatchLog.class));
        verify(eventPublisher).publishEvent(any(TradeCollectionCompletedEvent.class));
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
}
