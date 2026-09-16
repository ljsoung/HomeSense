package com.jiseong.homesense.batch.matcher;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.LocalDateTime;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.jiseong.homesense.batch.matcher.TradeRematchBatchProcessor.BatchOutcome;
import com.jiseong.homesense.batch.matcher.TradeRematchRunner.RematchSummary;

/**
 * 실제 매칭/갱신 로직은 {@link TradeRematchBatchProcessorTest}가 검증한다 — 이 클래스는 그 배치
 * 처리기를 트레이드 아이디 커서로 몇 번 호출하며 결과를 누적하는 오케스트레이션만 검증한다.
 */
@ExtendWith(MockitoExtension.class)
class TradeRematchRunnerTest {

    @Mock
    private TradeRematchBatchProcessor batchProcessor;

    @InjectMocks
    private TradeRematchRunner runner;

    @Test
    void 여러_배치의_unchanged_changed를_누적해_요약한다() {
        when(batchProcessor.processUnmatchedBatch(eq(0L)))
                .thenReturn(new BatchOutcome(10L, 3, 2, true));
        when(batchProcessor.processUnmatchedBatch(eq(10L)))
                .thenReturn(new BatchOutcome(20L, 1, 4, true));
        when(batchProcessor.processUnmatchedBatch(eq(20L)))
                .thenReturn(BatchOutcome.empty());

        RematchSummary summary = runner.rematchUnmatched();

        assertThat(summary.unchanged()).isEqualTo(4);
        assertThat(summary.changed()).isEqualTo(6);
        verify(batchProcessor, times(3)).processUnmatchedBatch(any());
    }

    @Test
    void 배치가_처음부터_비어있으면_0으로_요약하고_한_번만_호출한다() {
        when(batchProcessor.processUnmatchedBatch(eq(0L))).thenReturn(BatchOutcome.empty());

        RematchSummary summary = runner.rematchUnmatched();

        assertThat(summary.unchanged()).isZero();
        assertThat(summary.changed()).isZero();
        verify(batchProcessor, times(1)).processUnmatchedBatch(any());
    }

    @Test
    void 두번째_패스는_cutoff를_그대로_배치_처리기에_전달하며_커서를_전진한다() {
        LocalDateTime cutoff = LocalDateTime.of(2026, 9, 16, 13, 0);
        when(batchProcessor.processUpdatedBeforeBatch(eq(cutoff), eq(0L)))
                .thenReturn(new BatchOutcome(15L, 5, 1, true));
        when(batchProcessor.processUpdatedBeforeBatch(eq(cutoff), eq(15L)))
                .thenReturn(BatchOutcome.empty());

        RematchSummary summary = runner.rematchUpdatedBefore(cutoff);

        assertThat(summary.unchanged()).isEqualTo(5);
        assertThat(summary.changed()).isEqualTo(1);
        verify(batchProcessor, times(2)).processUpdatedBeforeBatch(eq(cutoff), any());
    }
}
