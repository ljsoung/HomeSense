package com.jiseong.homesense.batch.scheduler;

import java.time.YearMonth;
import java.time.format.DateTimeFormatter;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Component;
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
import com.jiseong.homesense.batch.errorhandler.RetryFailureDetail;
import com.jiseong.homesense.batch.errorhandler.RetryOutcome;
import com.jiseong.homesense.batch.errorhandler.RetryQueueManager;
import com.jiseong.homesense.batch.repository.BatchLogRepository;
import com.jiseong.homesense.common.config.BatchSchedulerProperties;
import com.jiseong.homesense.region.repository.LegalDistrictCodeRepository;
import com.jiseong.homesense.trade.entity.DealCategory;
import com.jiseong.homesense.trade.entity.HousingType;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * BAT-SCH-01(2/2). 시군구(약 250여 개) × 계약월(전월+당월) × 주택유형 × 거래유형 조합을 순회하며
 * BAT-CLC-01(RealEstateApiCollector)을 호출하는 실행 루프 제어기. 30 TPS 제한을 넘지 않도록
 * throttle()로 호출 간격을 조절하고, 조합 단위 실패는 건너뛰되 서비스키 오류(30/31)가 연속으로
 * 쌓이면 전체를 조기 중단한다. RETRY 판정(resultCode 01/02/04/05/22)과 data.go.kr의 HTTP
 * 오류(429/5xx)·커넥션 타임아웃 같은 전송 계층 실패(RestClientException)는 그 자리에서 블로킹
 * 재시도하지 않고 BAT-ERR-01(RetryQueueManager)의 큐에 적재만 해두고 다음 조합으로 즉시 넘어간다 —
 * 전체 조합 순회가 끝난 뒤 큐를 한 번에 처리해 분 단위 백오프가 순회 자체를 지연시키지 않게 한다.
 */
@Slf4j
@Component
@RequiredArgsConstructor
class BatchExecutionOrchestrator {

    private static final DateTimeFormatter DEAL_YMD_FORMATTER = DateTimeFormatter.ofPattern("yyyyMM");

    /**
     * 서비스키 오류(30/31)가 단발성 오탐일 가능성을 배제하기 위한 연속 발생 임계치.
     * 실제로 키가 무효/만료면 다음 조합에서도 같은 오류가 반복되므로 몇 건 안에 이 값에 도달한다.
     */
    private static final int CONSECUTIVE_AUTH_FAILURE_THRESHOLD = 3;

    private static final int RESULT_MESSAGE_MAX_LENGTH = 200;

    private final LegalDistrictCodeRepository legalDistrictCodeRepository;
    private final RealEstateApiCollector collector;
    private final DatasetRegistry datasetRegistry;
    private final BatchLogRepository batchLogRepository;
    private final BatchSchedulerProperties batchSchedulerProperties;
    private final ApplicationEventPublisher eventPublisher;
    private final ApiCallThrottle apiCallThrottle;
    private final RetryQueueManager retryQueueManager;

    private int consecutiveAbortBatchCount = 0;

    /**
     * targetMonth를 기준으로 "전월 + 당월" 2개월을 계약월 축으로 삼아 재수집한다 — 국토부 자료가
     * 계약일 기준으로 소급 등록되는 특성(지연 신고) 때문에 당월 한 달만 보면 최근 신고분을 놓친다.
     */
    void orchestrate(YearMonth targetMonth) {
        // 이전 실행이 어떤 이유로든(CriticalBatchException 외의 예외, 프로세스 재기동 등)
        // 큐를 비우지 못하고 끝났을 가능성에 대비해, 새 실행은 항상 빈 큐로 시작한다는 불변식을
        // 여기서도 보장한다 — CriticalBatchException catch 블록의 clear() 호출과 완전히
        // 중복되지만(정상 종료 후엔 빈 큐를 비우는 no-op), 둘 중 하나가 나중에 빠지더라도
        // 다른 하나가 안전망 역할을 계속하도록 이중화한다.
        retryQueueManager.clear().forEach(this::logRetryAbandoned);

        List<String> sggCds = legalDistrictCodeRepository.findDistinctActiveSggCd();
        List<YearMonth> targetMonths = List.of(targetMonth.minusMonths(1), targetMonth);
        List<HousingType> housingTypes = batchSchedulerProperties.housingTypes();
        consecutiveAbortBatchCount = 0;

        int combinationCount = 0;
        try {
            for (String sggCd : sggCds) {
                for (YearMonth month : targetMonths) {
                    String dealYmd = month.format(DEAL_YMD_FORMATTER);
                    for (HousingType housingType : housingTypes) {
                        for (DealCategory dealCategory : DealCategory.values()) {
                            processCombination(housingType, dealCategory, sggCd, dealYmd);
                            combinationCount++;
                        }
                    }
                }
            }
            retryQueueManager.processRetryQueue(this::attemptRetry, this::logRetryExhausted);
        } catch (CriticalBatchException e) {
            log.error("BAT-SCH-01 배치 조기 중단: {}", e.getMessage(), e);
            // 조기 중단으로 processRetryQueue()에 도달하지 못했다 — 그때까지 큐에 쌓인 항목을
            // 비우지 않으면 싱글턴인 RetryQueueManager에 그대로 남아 다음 배치 사이클의 큐와
            // 뒤섞인다(계약월 축이 이동해 이미 범위를 벗어난 dealYmd를 재수집할 수도 있다).
            retryQueueManager.clear().forEach(this::logRetryAbandoned);
            return;
        }

        log.info("BAT-SCH-01 조합 순회 완료: targetMonth={}, 처리 조합 수={}", targetMonth, combinationCount);
        eventPublisher.publishEvent(new TradeCollectionCompletedEvent(targetMonth));
    }

    /**
     * 호출 간 최소 지연을 적용한다(30 TPS 대응). 실제 요청은 RealEstateApiCollector가 데이터셋·페이지
     * 단위로 여러 번 내보낼 수 있으므로, 여기서 쓰는 ApiCallThrottle은 RealEstateApiCollector와
     * 같은 싱글턴 인스턴스를 공유한다 — 그래야 조합 경계뿐 아니라 그 안의 모든 실제 HTTP 요청까지
     * 하나의 시계로 간격이 지켜진다.
     */
    void throttle() {
        apiCallThrottle.throttle();
    }

    private void processCombination(HousingType housingType, DealCategory dealCategory, String sggCd, String dealYmd) {
        throttle();
        try {
            ApiResponseXml response = collector.collect(housingType, dealCategory, sggCd, dealYmd);
            consecutiveAbortBatchCount = 0;
            logSuccess(housingType, dealCategory, sggCd, dealYmd, response);
        } catch (OpenApiResultCodeException e) {
            if (e.judgment() == ErrorCodeJudgment.RETRY) {
                // 지금 당장 블로킹으로 재시도하지 않고 큐에 적재만 해두고 다음 조합으로 넘어간다 —
                // 실제 재시도는 전체 순회가 끝난 뒤 RetryQueueManager.processRetryQueue()가 수행한다.
                consecutiveAbortBatchCount = 0;
                retryQueueManager.enqueueRetry(new CollectRequest(housingType, dealCategory, sggCd, dealYmd));
                return;
            }
            handleResultCodeFailure(housingType, dealCategory, sggCd, dealYmd, e);
        } catch (OpenApiResponseException e) {
            // 응답 구조 자체를 못 읽은 경우라 어느 데이터셋인지 정확히 알 수 없다 — 조합의 대표(첫)
            // 데이터셋으로 귀속시켜 기록한다.
            logStructuralFailure(housingType, dealCategory, sggCd, dealYmd, e.getMessage());
        } catch (RestClientException e) {
            // data.go.kr의 HTTP 오류(429/5xx)·커넥션 타임아웃/리셋 등 전송 계층 실패. resultCode 자체를
            // 못 받았으므로 OpenApiResultCodeException/OpenApiResponseException 어느 쪽에도 안 걸리지만,
            // 재시도로 회복 가능한 일시적 오류라는 성격은 RETRY 판정과 같아 마찬가지로 큐에 적재한다.
            consecutiveAbortBatchCount = 0;
            retryQueueManager.enqueueRetry(new CollectRequest(housingType, dealCategory, sggCd, dealYmd));
        }
    }

    /**
     * RetryQueueManager가 백오프 이후 재시도할 때 호출하는 콜백. resolved()면 큐에서 제거되고
     * (성공했거나 더 이상 RETRY 대상이 아님), 아니면 이번 시도에서 관측한 실제 API 오류
     * (datasetId·resultCode·message)를 failureDetail에 담아 돌려준다 — 재시도가 결국 소진될
     * 경우 이 정보가 logRetryExhausted까지 전달돼 batch_log에 "N/A" 대신 실제 오류가 남는다.
     */
    private RetryOutcome attemptRetry(CollectRequest request) {
        try {
            ApiResponseXml response = collector.collect(
                    request.housingType(), request.dealCategory(), request.sggCd(), request.dealYmd());
            consecutiveAbortBatchCount = 0;
            logSuccess(request.housingType(), request.dealCategory(), request.sggCd(), request.dealYmd(), response);
            return RetryOutcome.success();
        } catch (OpenApiResultCodeException e) {
            if (e.judgment() == ErrorCodeJudgment.RETRY) {
                return RetryOutcome.stillFailing(new RetryFailureDetail(e.datasetId(), e.resultCode(), e.getMessage()));
            }
            handleResultCodeFailure(request.housingType(), request.dealCategory(), request.sggCd(), request.dealYmd(), e);
            return RetryOutcome.success();
        } catch (OpenApiResponseException e) {
            logStructuralFailure(request.housingType(), request.dealCategory(), request.sggCd(), request.dealYmd(),
                    e.getMessage());
            return RetryOutcome.success();
        } catch (RestClientException e) {
            // 전송 계층 실패는 특정 데이터셋의 API 오류가 아니라 datasetId·resultCode로 귀속시킬
            // 수 없다 — 메시지만 보존하고, 최종 소진 시엔 대표 데이터셋과 "N/A"로 기록된다.
            return RetryOutcome.stillFailing(RetryFailureDetail.generic(e.getMessage()));
        }
    }

    /**
     * 재시도 큐의 backoffSchedule을 소진했거나(최대 재시도 횟수 초과), RetryQueueManager의
     * 대기 예산(maxTotalWaitMinutes)을 넘어 더 이상 대기·재시도하지 않고 넘어온 최종 실패.
     * detail에 실제 API 오류(datasetId·resultCode)가 남아 있으면 그대로 batch_log에 기록해,
     * 어떤 데이터셋의 어떤 오류로 재시도가 실패했는지 보존한다 — 전송 계층 실패나 한 번도
     * 시도되지 못한 채 대기 예산을 넘긴 경우에만 대표 데이터셋과 "N/A"로 대체한다. 같은 조합은
     * 다음 배치 사이클(익일 재수집)에서 자연히 다시 시도된다.
     */
    private void logRetryExhausted(CollectRequest request, RetryFailureDetail detail) {
        consecutiveAbortBatchCount = 0;
        logFailure(request.housingType(), request.dealCategory(), request.sggCd(), request.dealYmd(),
                resolveDatasetId(request, detail), resolveResultCode(detail),
                appendRolloverNote(detail, "재시도 소진 또는 재시도 대기 예산 초과 — 다음 배치 사이클로 이월"));
    }

    /**
     * 배치가 CriticalBatchException으로 조기 중단돼 처리되지 못한 채 재시도 큐에서 비워진
     * 요청. 중단 전에 이미 한 번 이상 재시도돼 실제 API 오류를 관측했다면(pending.lastFailure())
     * 그 정보를 그대로 기록하고, 아직 한 번도 시도되지 못했다면 대표 데이터셋과 "N/A"로
     * 대체한다. 큐에 그대로 남겨두면 다음 배치 사이클의 큐와 뒤섞이므로 여기서 실패로 기록하고
     * 다음 배치 사이클(익일 재수집)로 이월시킨다.
     */
    private void logRetryAbandoned(RetryQueueManager.PendingRetry pending) {
        CollectRequest request = pending.request();
        RetryFailureDetail detail = pending.lastFailure();
        logFailure(request.housingType(), request.dealCategory(), request.sggCd(), request.dealYmd(),
                resolveDatasetId(request, detail), resolveResultCode(detail),
                appendRolloverNote(detail, "배치 조기 중단으로 재시도 취소 — 다음 배치 사이클로 이월"));
    }

    private String resolveDatasetId(CollectRequest request, RetryFailureDetail detail) {
        if (detail != null && detail.datasetId() != null) {
            return detail.datasetId();
        }
        return datasetRegistry.resolve(request.housingType(), request.dealCategory()).get(0).datasetId();
    }

    private String resolveResultCode(RetryFailureDetail detail) {
        return detail != null && detail.resultCode() != null ? detail.resultCode() : "N/A";
    }

    private String appendRolloverNote(RetryFailureDetail detail, String rolloverNote) {
        if (detail != null && detail.message() != null) {
            return detail.message() + " — " + rolloverNote;
        }
        return rolloverNote;
    }

    private void logStructuralFailure(HousingType housingType, DealCategory dealCategory, String sggCd,
                                       String dealYmd, String message) {
        // 서비스키 문제가 아니므로 연속 실패 카운트는 리셋한다.
        consecutiveAbortBatchCount = 0;
        String representativeDatasetId = datasetRegistry.resolve(housingType, dealCategory).get(0).datasetId();
        logFailure(housingType, dealCategory, sggCd, dealYmd, representativeDatasetId, "N/A", message);
    }

    private void handleResultCodeFailure(HousingType housingType, DealCategory dealCategory, String sggCd,
                                          String dealYmd, OpenApiResultCodeException e) {
        if (e.judgment() == ErrorCodeJudgment.ABORT_BATCH) {
            consecutiveAbortBatchCount++;
        } else {
            consecutiveAbortBatchCount = 0;
        }
        logFailure(housingType, dealCategory, sggCd, dealYmd, e.datasetId(), e.resultCode(), e.getMessage());

        if (consecutiveAbortBatchCount >= CONSECUTIVE_AUTH_FAILURE_THRESHOLD) {
            throw new CriticalBatchException(e.resultCode(), consecutiveAbortBatchCount);
        }
    }

    /**
     * ApiResponseXml은 조합 하나(APT+SALE이면 기본·상세 2개 데이터셋)의 페이지를 이어붙인 것이라,
     * batch_log의 단일 dataset_id 컬럼에 맞춰 데이터셋별로 나눠 한 행씩 기록한다. 이 레이어는
     * XML 페이지 단위까지만 알고 있어 processedCount는 페이지 수를 기록한다(실제 항목 수는 BAT-PRS-01 책임).
     */
    private void logSuccess(HousingType housingType, DealCategory dealCategory, String sggCd, String dealYmd,
                             ApiResponseXml response) {
        Map<String, Long> pageCountByDataset = response.pages().stream()
                .collect(Collectors.groupingBy(DatasetPage::datasetId, LinkedHashMap::new, Collectors.counting()));
        pageCountByDataset.forEach((datasetId, pageCount) -> {
            BatchLog batchLog = BatchLog.start(housingType, dealCategory, sggCd, dealYmd, datasetId);
            batchLog.finish("000", null, true, pageCount.intValue(), 0);
            batchLogRepository.save(batchLog);
        });
    }

    private void logFailure(HousingType housingType, DealCategory dealCategory, String sggCd, String dealYmd,
                             String datasetId, String resultCode, String resultMessage) {
        BatchLog batchLog = BatchLog.start(housingType, dealCategory, sggCd, dealYmd, datasetId);
        batchLog.finish(resultCode, truncate(resultMessage), false, 0, 1);
        batchLogRepository.save(batchLog);
    }

    private String truncate(String message) {
        if (message == null || message.length() <= RESULT_MESSAGE_MAX_LENGTH) {
            return message;
        }
        return message.substring(0, RESULT_MESSAGE_MAX_LENGTH);
    }
}
