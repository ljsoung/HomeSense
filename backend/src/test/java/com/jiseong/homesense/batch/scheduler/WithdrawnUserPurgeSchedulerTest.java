package com.jiseong.homesense.batch.scheduler;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Clock;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.List;
import java.util.stream.LongStream;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InOrder;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;

import com.jiseong.homesense.common.config.WithdrawalProperties;
import com.jiseong.homesense.user.service.WithdrawalPolicy;
import com.jiseong.homesense.user.service.WithdrawnUserPurgeService;

/**
 * 반복문(청크 순회·keyset 커서·개별 실패 격리·집계)만 검증한다 — 실제로 DB에서 행과 자식이 지워지는지는
 * Mockito로 증명할 수 없어 {@code WithdrawnUserPurgeMariaDbIT}가 맡는다.
 */
@ExtendWith({MockitoExtension.class, OutputCaptureExtension.class})
class WithdrawnUserPurgeSchedulerTest {

    private static final ZoneId KST = ZoneId.of("Asia/Seoul");
    private static final LocalDateTime NOW = LocalDateTime.of(2026, 9, 21, 5, 0, 0);
    private static final LocalDateTime THRESHOLD = NOW.minusDays(7);

    @Mock
    private WithdrawnUserPurgeService purgeService;

    private WithdrawnUserPurgeScheduler scheduler;

    @BeforeEach
    void setUp() {
        Clock clock = Clock.fixed(NOW.atZone(KST).toInstant(), KST);
        WithdrawalPolicy policy = new WithdrawalPolicy(clock,
                new WithdrawalProperties(7, new WithdrawalProperties.Purge(true, "0 0 5 * * *")));
        scheduler = new WithdrawnUserPurgeScheduler(purgeService, policy, clock);
    }

    @Test
    void 대상이_없으면_아무것도_삭제하지_않고_요약만_남긴다(CapturedOutput output) {
        when(purgeService.findTargetIds(THRESHOLD, 0L, WithdrawnUserPurgeScheduler.CHUNK_SIZE)).thenReturn(List.of());

        scheduler.runDailyPurge();

        verify(purgeService, never()).purgeOne(anyLong(), org.mockito.ArgumentMatchers.any());
        assertThat(output.getAll()).contains("WITHDRAWN_USER_PURGE completed purged=0 skipped=0 failed=0");
    }

    @Test
    void 파기_threshold는_현재에서_유예일수를_뺀_값이고_keyset_커서는_마지막_ID로_전진한다() {
        when(purgeService.findTargetIds(THRESHOLD, 0L, 100)).thenReturn(List.of(3L, 7L, 9L));
        when(purgeService.findTargetIds(THRESHOLD, 9L, 100)).thenReturn(List.of());
        when(purgeService.purgeOne(anyLong(), eq(THRESHOLD))).thenReturn(1);

        scheduler.runDailyPurge();

        InOrder order = inOrder(purgeService);
        order.verify(purgeService).findTargetIds(THRESHOLD, 0L, 100);
        order.verify(purgeService).purgeOne(3L, THRESHOLD);
        order.verify(purgeService).purgeOne(7L, THRESHOLD);
        order.verify(purgeService).purgeOne(9L, THRESHOLD);
        order.verify(purgeService).findTargetIds(THRESHOLD, 9L, 100);
    }

    @Test
    void 청크_크기를_넘는_대상은_여러_청크로_나눠_모두_처리한다(CapturedOutput output) {
        List<Long> firstChunk = LongStream.rangeClosed(1, 100).boxed().toList();
        List<Long> secondChunk = LongStream.rangeClosed(101, 105).boxed().toList();
        when(purgeService.findTargetIds(THRESHOLD, 0L, 100)).thenReturn(firstChunk);
        when(purgeService.findTargetIds(THRESHOLD, 100L, 100)).thenReturn(secondChunk);
        when(purgeService.findTargetIds(THRESHOLD, 105L, 100)).thenReturn(List.of());
        when(purgeService.purgeOne(anyLong(), eq(THRESHOLD))).thenReturn(1);

        scheduler.runDailyPurge();

        verify(purgeService, times(105)).purgeOne(anyLong(), eq(THRESHOLD));
        assertThat(output.getAll()).contains("purged=105 skipped=0 failed=0");
    }

    @Test
    void 한_명이_예외를_내도_나머지를_계속_처리하고_실패로_집계한다(CapturedOutput output) {
        when(purgeService.findTargetIds(THRESHOLD, 0L, 100)).thenReturn(List.of(1L, 2L, 3L));
        when(purgeService.findTargetIds(THRESHOLD, 3L, 100)).thenReturn(List.of());
        when(purgeService.purgeOne(1L, THRESHOLD)).thenReturn(1);
        when(purgeService.purgeOne(2L, THRESHOLD)).thenThrow(new IllegalStateException("db down"));
        when(purgeService.purgeOne(3L, THRESHOLD)).thenReturn(1);

        scheduler.runDailyPurge();

        verify(purgeService).purgeOne(3L, THRESHOLD); // 2번이 실패해도 3번까지 진행
        assertThat(output.getAll())
                .contains("WITHDRAWN_USER_PURGE failed userId=2")
                .contains("purged=2 skipped=0 failed=1");
    }

    @Test
    void 실패한_행이_계속_조회되더라도_커서가_전진해_반복이_반드시_끝난다() {
        // 실제 keyset 쿼리처럼 afterId보다 큰 ID만 돌려준다 — 1번이 계속 실패해도 무한 루프가 되지 않아야 한다.
        when(purgeService.findTargetIds(eq(THRESHOLD), anyLong(), anyInt())).thenAnswer(invocation -> {
            long afterId = invocation.getArgument(1);
            return afterId < 1L ? List.of(1L) : List.of();
        });
        when(purgeService.purgeOne(1L, THRESHOLD)).thenThrow(new IllegalStateException("fk"));

        scheduler.runDailyPurge();

        verify(purgeService, times(1)).purgeOne(1L, THRESHOLD);
        verify(purgeService, times(2)).findTargetIds(eq(THRESHOLD), anyLong(), anyInt());
    }

    @Test
    void 조회_이후_철회되거나_이미_삭제돼_0건이면_skipped로_센다(CapturedOutput output) {
        when(purgeService.findTargetIds(THRESHOLD, 0L, 100)).thenReturn(List.of(1L, 2L));
        when(purgeService.findTargetIds(THRESHOLD, 2L, 100)).thenReturn(List.of());
        when(purgeService.purgeOne(1L, THRESHOLD)).thenReturn(0);
        when(purgeService.purgeOne(2L, THRESHOLD)).thenReturn(1);

        scheduler.runDailyPurge();

        assertThat(output.getAll()).contains("purged=1 skipped=1 failed=0");
    }

    @Test
    void withdrawn_at이_없는_WITHDRAWN_행은_삭제하지_않고_WARN으로_건수만_남긴다(CapturedOutput output) {
        when(purgeService.countWithdrawnWithoutTimestamp()).thenReturn(2L);
        when(purgeService.findTargetIds(THRESHOLD, 0L, 100)).thenReturn(List.of());

        scheduler.runDailyPurge();

        // 이 행들은 대상 조회(findTargetIds)에 잡히지 않으므로 purgeOne 호출 자체가 없다.
        verify(purgeService, never()).purgeOne(anyLong(), org.mockito.ArgumentMatchers.any());
        assertThat(output.getAll())
                .contains("WITHDRAWN_USER_PURGE skipped rows with status=WITHDRAWN and withdrawn_at IS NULL count=2")
                .contains("malformedWithdrawn=2");
    }
}
