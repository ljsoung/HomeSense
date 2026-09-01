package com.jiseong.homesense.batch.scheduler;

import java.time.YearMonth;

import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import lombok.RequiredArgsConstructor;

/**
 * BAT-SCH-01(1/2). 배치 파이프라인 전체의 @Scheduled 진입점.
 * 매일 새벽 3시(트래픽이 낮은 시간대)에 기동해 BatchExecutionOrchestrator에 실행을 위임한다.
 */
@Component
@RequiredArgsConstructor
public class TradeCollectionScheduler {

    private final BatchExecutionOrchestrator orchestrator;

    @Scheduled(cron = "0 0 3 * * *")
    void runDailyCollection() {
        orchestrator.orchestrate(YearMonth.now());
    }
}
