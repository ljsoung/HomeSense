package com.jiseong.homesense.batch.scheduler;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.YearMonth;
import java.time.ZoneId;
import java.util.List;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.jiseong.homesense.batch.repository.BatchLogRepository;
import com.jiseong.homesense.region.repository.LegalDistrictCodeRepository;

@ExtendWith(MockitoExtension.class)
class BackfillCommandLineRunnerTest {

    private static final ZoneId KST = ZoneId.of("Asia/Seoul");

    @Mock
    private LegalDistrictCodeRepository legalDistrictCodeRepository;

    @Mock
    private BatchLogRepository batchLogRepository;

    @Mock
    private BatchExecutionOrchestrator orchestrator;

    @InjectMocks
    private BackfillCommandLineRunner runner;

    @Test
    void 인자가_없으면_활성코드에서_batch_log가_이미_다룬_코드를_뺀_차집합을_대상으로_삼는다() {
        when(legalDistrictCodeRepository.findDistinctActiveSggCd())
                .thenReturn(List.of("41597", "12000", "11110", "28125"));
        when(batchLogRepository.findDistinctLawdCd()).thenReturn(List.of("11110"));

        runner.run();

        ArgumentCaptor<List<String>> codesCaptor = ArgumentCaptor.forClass(List.class);
        verify(orchestrator).orchestrateBackfill(codesCaptor.capture(), anyList());
        // 11110은 이미 batch_log에 있으므로 제외되고, 나머지는 정렬돼 남는다.
        assertThat(codesCaptor.getValue()).containsExactly("12000", "28125", "41597");
    }

    @Test
    void 인자가_없으면_2026년_2월부터_오늘이_속한_월까지_균일하게_대상월로_삼는다() {
        when(legalDistrictCodeRepository.findDistinctActiveSggCd()).thenReturn(List.of());
        when(batchLogRepository.findDistinctLawdCd()).thenReturn(List.of());

        runner.run();

        ArgumentCaptor<List<YearMonth>> monthsCaptor = ArgumentCaptor.forClass(List.class);
        verify(orchestrator).orchestrateBackfill(anyList(), monthsCaptor.capture());
        YearMonth expectedEnd = YearMonth.now(KST);
        assertThat(monthsCaptor.getValue()).first().isEqualTo(YearMonth.of(2026, 2));
        assertThat(monthsCaptor.getValue()).last().isEqualTo(expectedEnd);
        assertThat(monthsCaptor.getValue()).isSorted();
    }

    @Test
    void code_인자를_주면_차집합_도출을_건너뛰고_그_코드_하나만_대상으로_삼는다() {
        runner.run("--code=41591");

        ArgumentCaptor<List<String>> codesCaptor = ArgumentCaptor.forClass(List.class);
        verify(orchestrator).orchestrateBackfill(codesCaptor.capture(), anyList());
        assertThat(codesCaptor.getValue()).containsExactly("41591");
    }

    @Test
    void month_인자를_반복_지정하면_그_달들만_정확히_대상으로_삼는다() {
        // 시범 실행이 batch_log를 오염시켜 전체 실행의 차집합 도출에서 특정 코드가 통째로 빠지는
        // 문제(2026-09-18 실행에서 실제로 겪음)를 보충 실행으로 메울 때 쓰는 경로다.
        runner.run("--code=41591", "--month=202603", "--month=202604", "--month=202609");

        ArgumentCaptor<List<YearMonth>> monthsCaptor = ArgumentCaptor.forClass(List.class);
        verify(orchestrator).orchestrateBackfill(anyList(), monthsCaptor.capture());
        assertThat(monthsCaptor.getValue()).containsExactly(
                YearMonth.of(2026, 3), YearMonth.of(2026, 4), YearMonth.of(2026, 9));
    }
}
