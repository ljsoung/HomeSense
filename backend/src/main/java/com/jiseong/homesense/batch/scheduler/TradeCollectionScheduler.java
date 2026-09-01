package com.jiseong.homesense.batch.scheduler;

import java.time.YearMonth;
import java.time.ZoneId;

import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import lombok.RequiredArgsConstructor;

/**
 * BAT-SCH-01(1/2). 배치 파이프라인 전체의 @Scheduled 진입점.
 * 매일 새벽 3시(트래픽이 낮은 시간대, KST 기준)에 기동해 BatchExecutionOrchestrator에 실행을 위임한다.
 */
@Component
@RequiredArgsConstructor
public class TradeCollectionScheduler {

    // JVM 기본 타임존이 UTC인 배포 환경에서도 cron·대상월 계산이 항상 KST 기준이 되도록 명시한다.
    // spring.jackson.time-zone은 JSON 직렬화에만 영향을 줄 뿐 @Scheduled·YearMonth.now()에는
    // 적용되지 않는다.
    private static final ZoneId KST = ZoneId.of("Asia/Seoul");

    private final BatchExecutionOrchestrator orchestrator;

    @Scheduled(cron = "0 0 3 * * *", zone = "Asia/Seoul")
    void runDailyCollection() {
        orchestrator.orchestrate(YearMonth.now(KST));
    }
}
