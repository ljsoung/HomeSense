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
import com.jiseong.homesense.batch.collector.BatchInterruptedException;
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

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * BAT-SCH-01(2/2). 시군구(약 250여 개) × 계약월(전월+당월) × 주택유형 × 거래유형 조합을 순회하며
 * BAT-CLC-01(RealEstateApiCollector)을 호출하는 실행 루프 제어기. 30 TPS 제한을 넘지 않도록
 * throttle()로 호출 간격을 조절하고, 조합 단위 실패는 건너뛰되 서비스키 오류(30/31)가 연속으로
 * 쌓이면 전체를 조기 중단한다. data.go.kr의 HTTP 오류(429/5xx)·커넥션 타임아웃 같은 전송 계층
 * 실패(RestClientException)도 resultCode 판정과 별개로 재시도 후 조합 단위 실패로 격리해, 일시적인
 * 네트워크 장애 하나로 하루치 전체 배치가 중단되지 않게 한다.
 */
@Slf4j
@Component
@RequiredArgsConstructor
class BatchExecutionOrchestrator {

    private static final DateTimeFormatter DEAL_YMD_FORMATTER = DateTimeFormatter.ofPattern("yyyyMM");

    private static final int MAX_RETRY_ATTEMPTS = 3;
    private static final long RETRY_BASE_BACKOFF_MILLIS = 200;

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

    private int consecutiveAbortBatchCount = 0;

    /**
     * targetMonth를 기준으로 "전월 + 당월" 2개월을 계약월 축으로 삼아 재수집한다 — 국토부 자료가
     * 계약일 기준으로 소급 등록되는 특성(지연 신고) 때문에 당월 한 달만 보면 최근 신고분을 놓친다.
     */
    void orchestrate(YearMonth targetMonth) {
        List<String> sggCds = legalDistrictCodeRepository.findDistinctActiveSggCd();
        List<YearMonth> targetMonths = List.of(targetMonth.minusMonths(1), targetMonth);
        List<HousingType> housingTypes = batchSchedulerProperties.housingTypes();
        consecutiveAbortBatchCount = 0;

        int combinationCount = 0;
        for (String sggCd : sggCds) {
            for (YearMonth month : targetMonths) {
                String dealYmd = month.format(DEAL_YMD_FORMATTER);
                for (HousingType housingType : housingTypes) {
                    for (DealCategory dealCategory : DealCategory.values()) {
                        try {
                            processCombination(housingType, dealCategory, sggCd, dealYmd);
                            combinationCount++;
                        } catch (CriticalBatchException e) {
                            log.error("BAT-SCH-01 배치 조기 중단: {}", e.getMessage(), e);
                            return;
                        }
                    }
                }
            }
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
            ApiResponseXml response = collectWithRetry(housingType, dealCategory, sggCd, dealYmd);
            consecutiveAbortBatchCount = 0;
            logSuccess(housingType, dealCategory, sggCd, dealYmd, response);
        } catch (OpenApiResultCodeException e) {
            handleResultCodeFailure(housingType, dealCategory, sggCd, dealYmd, e);
        } catch (OpenApiResponseException e) {
            // 응답 구조 자체를 못 읽은 경우라 어느 데이터셋인지 정확히 알 수 없다 — 조합의 대표(첫)
            // 데이터셋으로 귀속시켜 기록한다.
            logStructuralFailure(housingType, dealCategory, sggCd, dealYmd, e.getMessage());
        } catch (RestClientException e) {
            // data.go.kr의 HTTP 오류(429/5xx)·커넥션 타임아웃/리셋 등 전송 계층 실패. resultCode 자체를
            // 못 받았으므로 OpenApiResultCodeException/OpenApiResponseException 어느 쪽에도 안 걸리지만,
            // 재시도로 회복 가능한 일시적 오류라는 성격은 RETRY 판정과 같아 collectWithRetry에서 이미
            // 재시도를 소진한 뒤 여기로 온다 — 조합 단위 실패로만 기록하고 배치 전체는 계속 진행한다.
            logStructuralFailure(housingType, dealCategory, sggCd, dealYmd, "전송 오류: " + e.getMessage());
        }
    }

    private void logStructuralFailure(HousingType housingType, DealCategory dealCategory, String sggCd,
                                       String dealYmd, String message) {
        // 서비스키 문제가 아니므로 연속 실패 카운트는 리셋한다.
        consecutiveAbortBatchCount = 0;
        String representativeDatasetId = datasetRegistry.resolve(housingType, dealCategory).get(0).datasetId();
        logFailure(housingType, dealCategory, sggCd, dealYmd, representativeDatasetId, "N/A", message);
    }

    private ApiResponseXml collectWithRetry(HousingType housingType, DealCategory dealCategory, String sggCd, String dealYmd) {
        int attempt = 0;
        while (true) {
            try {
                return collector.collect(housingType, dealCategory, sggCd, dealYmd);
            } catch (OpenApiResultCodeException e) {
                boolean canRetry = e.judgment() == ErrorCodeJudgment.RETRY && attempt < MAX_RETRY_ATTEMPTS;
                if (!canRetry) {
                    throw e;
                }
                attempt++;
                sleep(RETRY_BASE_BACKOFF_MILLIS * (1L << (attempt - 1)));
            } catch (RestClientException e) {
                if (attempt >= MAX_RETRY_ATTEMPTS) {
                    throw e;
                }
                attempt++;
                sleep(RETRY_BASE_BACKOFF_MILLIS * (1L << (attempt - 1)));
            }
        }
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

    private void sleep(long millis) {
        try {
            Thread.sleep(millis);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new BatchInterruptedException("재시도 백오프 대기 중 인터럽트됨", e);
        }
    }
}
