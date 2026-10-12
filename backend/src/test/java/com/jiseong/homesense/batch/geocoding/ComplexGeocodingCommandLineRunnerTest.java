package com.jiseong.homesense.batch.geocoding;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.List;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.ApplicationContext;

import com.jiseong.homesense.batch.geocoding.ComplexGeocodingService.RunResult;
import com.jiseong.homesense.batch.geocoding.ComplexGeocodingService.StopReason;

@ExtendWith(MockitoExtension.class)
class ComplexGeocodingCommandLineRunnerTest {

    @Mock
    private ComplexGeocodingService geocodingService;
    @Mock
    private ApplicationContext applicationContext;
    @InjectMocks
    private ComplexGeocodingCommandLineRunner runner;

    private static RunResult result(StopReason reason) {
        return new RunResult(0, 0, 0, 0, 0, 0, 0, 0, 0, reason);
    }

    @Test
    void 기본은_dry_run이라_카카오를_호출하지_않는다() {
        assertThat(runner.execute(List.of())).isZero();

        verify(geocodingService).dryRun();
        verify(geocodingService, never()).run(anyInt(), anyBoolean());
    }

    @Test
    void apply와_limit을_주면_그_수만큼만_보낸다() {
        when(geocodingService.run(30, false)).thenReturn(result(StopReason.MAX_COMPLEXES));

        assertThat(runner.execute(List.of("--apply", "--limit=30"))).isZero();
    }

    @Test
    void apply만_주면_제한_없이_실행하고_일일_상한_도달은_성공으로_끝난다() {
        when(geocodingService.run(Integer.MAX_VALUE, false)).thenReturn(result(StopReason.DAILY_LIMIT));

        assertThat(runner.execute(List.of("--apply"))).isZero();
    }

    @Test
    void 인증_오류로_멈추면_종료_코드_1이다() {
        when(geocodingService.run(Integer.MAX_VALUE, false)).thenReturn(result(StopReason.CONFIGURATION_ERROR));

        assertThat(runner.execute(List.of("--apply"))).isEqualTo(1);
    }

    @Test
    void 예외가_나면_종료_코드_1이다() {
        when(geocodingService.run(Integer.MAX_VALUE, false)).thenThrow(new IllegalStateException("Redis 장애"));

        assertThat(runner.execute(List.of("--apply"))).isEqualTo(1);
    }

    @Test
    void 카카오_요청_한도_429로_멈추면_종료_코드_1이다() {
        when(geocodingService.run(Integer.MAX_VALUE, false)).thenReturn(result(StopReason.RATE_LIMITED));

        assertThat(runner.execute(List.of("--apply"))).isEqualTo(1);
    }

    @Test
    void retry_failed를_주면_이전_실패_단지만_다시_처리한다() {
        when(geocodingService.run(Integer.MAX_VALUE, true)).thenReturn(result(StopReason.COMPLETED));

        assertThat(runner.execute(List.of("--apply", "--retry-failed"))).isZero();

        verify(geocodingService).run(Integer.MAX_VALUE, true);
    }
}
