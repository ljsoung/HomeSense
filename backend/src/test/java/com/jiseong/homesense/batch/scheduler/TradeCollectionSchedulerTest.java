package com.jiseong.homesense.batch.scheduler;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verify;

import java.time.YearMonth;
import java.time.ZoneId;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class TradeCollectionSchedulerTest {

    @Mock
    private BatchExecutionOrchestrator orchestrator;

    @InjectMocks
    private TradeCollectionScheduler scheduler;

    @Test
    void runDailyCollection은_KST_기준_당월로_orchestrate를_호출한다() {
        // 회귀 테스트: JVM 기본 타임존이 UTC인 배포에서도 대상월이 KST 기준이어야 한다 —
        // YearMonth.now()(시스템 기본 타임존)를 쓰면 자정 근처에 하루 어긋난 달을 고를 수 있었다.
        scheduler.runDailyCollection();

        ArgumentCaptor<YearMonth> captor = ArgumentCaptor.forClass(YearMonth.class);
        verify(orchestrator).orchestrate(captor.capture());
        assertThat(captor.getValue()).isEqualTo(YearMonth.now(ZoneId.of("Asia/Seoul")));
    }
}
