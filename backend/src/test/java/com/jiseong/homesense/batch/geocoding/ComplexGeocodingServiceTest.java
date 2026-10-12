package com.jiseong.homesense.batch.geocoding;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicLong;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.cache.Cache;
import org.springframework.cache.CacheManager;
import org.springframework.transaction.support.TransactionCallback;
import org.springframework.transaction.support.TransactionTemplate;

import com.jiseong.homesense.batch.geocoding.ComplexGeocodingService.StopReason;
import com.jiseong.homesense.common.cache.CacheNames;
import com.jiseong.homesense.common.config.GeocodingProperties;
import com.jiseong.homesense.common.logging.AuditLogger;
import com.jiseong.homesense.complex.repository.ComplexRepository;

@ExtendWith(MockitoExtension.class)
class ComplexGeocodingServiceTest {

    private static final Clock CLOCK = Clock.fixed(Instant.parse("2026-10-12T00:00:00Z"), ZoneId.of("Asia/Seoul"));
    private static final KakaoGeocodingClient.Found SEOUL =
            new KakaoGeocodingClient.Found(new BigDecimal("37.5726145"), new BigDecimal("126.9722730"));

    @Mock
    private ComplexRepository complexRepository;
    @Mock
    private KakaoGeocodingClient client;
    @Mock
    private GeocodingRedisState redisState;
    @Mock
    private TransactionTemplate transactionTemplate;
    @Mock
    private CacheManager cacheManager;
    @Mock
    private Cache detailCache;
    @Mock
    private AuditLogger auditLogger;

    private final AtomicLong todayCalls = new AtomicLong();
    private final List<Object[]> targets = new ArrayList<>();

    @BeforeEach
    void setUp() {
        lenient().when(complexRepository.findGeocodingTargets()).thenReturn(targets);
        lenient().when(redisState.recordCall()).thenAnswer(invocation -> todayCalls.incrementAndGet());
        lenient().when(transactionTemplate.execute(any())).thenAnswer(
                invocation -> ((TransactionCallback<?>) invocation.getArgument(0)).doInTransaction(null));
        lenient().when(cacheManager.getCache(CacheNames.COMPLEX_DETAIL)).thenReturn(detailCache);
        lenient().when(complexRepository.fillCoordinatesIfNull(anyLong(), any(), any(), anyString(), any()))
                .thenReturn(1);
    }

    private ComplexGeocodingService service(int dailyLimit, int maxRetries, int maxConsecutive, int chunkSize) {
        GeocodingProperties properties =
                new GeocodingProperties(0, dailyLimit, maxRetries, 0, maxConsecutive, 30, chunkSize, true);
        return new ComplexGeocodingService(complexRepository, client, redisState, properties, transactionTemplate,
                cacheManager, auditLogger, CLOCK);
    }

    private ComplexGeocodingService service() {
        return service(10_000, 2, 5, 100);
    }

    private void target(long complexId, String address, String dongRi) {
        targets.add(new Object[] {complexId, address, dongRi});
    }

    @Test
    void 좌표를_얻으면_PRECISE로_쓰고_단지_상세_캐시를_지운다() {
        target(1L, "서울특별시 종로구 내수동 73- 경희궁의아침4단지", "내수동");
        when(client.geocode("서울특별시 종로구 내수동 73")).thenReturn(SEOUL);

        ComplexGeocodingService.RunResult result = service().run(Integer.MAX_VALUE);

        verify(complexRepository).fillCoordinatesIfNull(1L, SEOUL.latitude(), SEOUL.longitude(), "PRECISE",
                LocalDateTime.now(CLOCK));
        verify(detailCache).evictIfPresent(1L);
        assertThat(result).isEqualTo(
                new ComplexGeocodingService.RunResult(1, 0, 0, 1, 1, 0, 0, 1, StopReason.COMPLETED));
    }

    @Test
    void 지번이_없거나_못_찾음_표시가_있는_단지는_호출하지_않는다() {
        target(1L, "서울특별시 중랑구 망우동  신내역 힐데스하임아파트", "망우동");
        target(2L, "서울특별시 종로구 내수동 72 경희궁의아침3단지", "내수동");
        when(redisState.isMarkedNotFound(2L)).thenReturn(true);

        ComplexGeocodingService.RunResult result = service().run(Integer.MAX_VALUE);

        verify(client, never()).geocode(anyString());
        assertThat(result.noJibun()).isEqualTo(1);
        assertThat(result.skipped()).isEqualTo(1);
        assertThat(result.calls()).isZero();
    }

    @Test
    void 주소를_못_찾으면_건너뛸_표시를_남기고_재시도하지_않는다() {
        target(1L, "서울특별시 종로구 내수동 72 경희궁의아침3단지", "내수동");
        when(client.geocode(anyString())).thenReturn(new KakaoGeocodingClient.NotFound("NO_RESULT"));

        ComplexGeocodingService.RunResult result = service().run(Integer.MAX_VALUE);

        verify(client, times(1)).geocode(anyString());
        verify(redisState).markNotFound(1L, "NO_RESULT");
        verify(auditLogger).logGeocodingFailure(1L, "서울특별시 종로구 내수동 72", "NO_RESULT", false);
        verify(complexRepository, never()).fillCoordinatesIfNull(anyLong(), any(), any(), anyString(), any());
        assertThat(result.notFound()).isEqualTo(1);
    }

    @Test
    void 일시_오류는_재시도하고_끝내_실패하면_표시_없이_다음_실행으로_넘긴다() {
        target(1L, "서울특별시 종로구 내수동 72 경희궁의아침3단지", "내수동");
        target(2L, "서울특별시 종로구 내수동 73 경희궁의아침4단지", "내수동");
        when(client.geocode("서울특별시 종로구 내수동 72")).thenThrow(new GeocodingTransientException("HTTP_429"));
        when(client.geocode("서울특별시 종로구 내수동 73"))
                .thenThrow(new GeocodingTransientException("HTTP_503"))
                .thenReturn(SEOUL);

        ComplexGeocodingService.RunResult result = service().run(Integer.MAX_VALUE);

        verify(client, times(3)).geocode("서울특별시 종로구 내수동 72"); // 처음 + 재시도 2회
        verify(redisState, never()).markNotFound(eq(1L), anyString());
        verify(auditLogger).logGeocodingFailure(1L, "서울특별시 종로구 내수동 72", "HTTP_429", true);
        assertThat(result.transientFailed()).isEqualTo(1);
        assertThat(result.found()).isEqualTo(1);
        assertThat(result.calls()).isEqualTo(5);
        assertThat(result.stopReason()).isEqualTo(StopReason.COMPLETED);
    }

    @Test
    void 일시_실패가_연달아_나오면_그_실행을_멈춘다() {
        for (long id = 1; id <= 5; id++) {
            target(id, "서울특별시 종로구 내수동 " + id + " 단지", "내수동");
        }
        when(client.geocode(anyString())).thenThrow(new GeocodingTransientException("HTTP_502"));

        ComplexGeocodingService.RunResult result = service(10_000, 0, 3, 100).run(Integer.MAX_VALUE);

        verify(client, times(3)).geocode(anyString());
        assertThat(result.stopReason()).isEqualTo(StopReason.CONSECUTIVE_TRANSIENT_FAILURES);
    }

    @Test
    void 일일_상한에_닿으면_보내지_않고_멈추며_받은_좌표는_쓴다() {
        target(1L, "서울특별시 종로구 내수동 72 경희궁의아침3단지", "내수동");
        target(2L, "서울특별시 종로구 내수동 73 경희궁의아침4단지", "내수동");
        todayCalls.set(9);
        when(client.geocode("서울특별시 종로구 내수동 72")).thenReturn(SEOUL);

        ComplexGeocodingService.RunResult result = service(10, 2, 5, 100).run(Integer.MAX_VALUE);

        verify(client, times(1)).geocode(anyString());
        verify(redisState).releaseCall();
        verify(complexRepository).fillCoordinatesIfNull(eq(1L), any(), any(), eq("PRECISE"), any());
        assertThat(result.stopReason()).isEqualTo(StopReason.DAILY_LIMIT);
        assertThat(result.written()).isEqualTo(1);
    }

    @Test
    void 인증_실패면_멈추고_그전에_받은_좌표는_쓴다() {
        target(1L, "서울특별시 종로구 내수동 72 경희궁의아침3단지", "내수동");
        target(2L, "서울특별시 종로구 내수동 73 경희궁의아침4단지", "내수동");
        target(3L, "서울특별시 종로구 내수동 74 경희궁의아침5단지", "내수동");
        when(client.geocode("서울특별시 종로구 내수동 72")).thenReturn(SEOUL);
        when(client.geocode("서울특별시 종로구 내수동 73")).thenThrow(new GeocodingConfigurationException("401"));

        ComplexGeocodingService.RunResult result = service().run(Integer.MAX_VALUE);

        verify(client, never()).geocode("서울특별시 종로구 내수동 74");
        verify(auditLogger).logBatchFailure(eq("BAT-GEO-01"), any(GeocodingConfigurationException.class));
        assertThat(result.stopReason()).isEqualTo(StopReason.CONFIGURATION_ERROR);
        assertThat(result.written()).isEqualTo(1);
    }

    @Test
    void 단지_수_상한이_있으면_그만큼만_보낸다() {
        target(1L, "서울특별시 종로구 내수동 72 경희궁의아침3단지", "내수동");
        target(2L, "서울특별시 중랑구 망우동  신내역 힐데스하임아파트", "망우동");
        target(3L, "서울특별시 종로구 내수동 73 경희궁의아침4단지", "내수동");
        when(client.geocode(anyString())).thenReturn(SEOUL);

        ComplexGeocodingService.RunResult result = service().run(1);

        verify(client, times(1)).geocode(anyString());
        assertThat(result.stopReason()).isEqualTo(StopReason.MAX_COMPLEXES);
        assertThat(result.noJibun()).isEqualTo(1);
    }

    @Test
    void 청크_크기마다_따로_커밋한다() {
        for (long id = 1; id <= 5; id++) {
            target(id, "서울특별시 종로구 내수동 " + id + " 단지", "내수동");
        }
        when(client.geocode(anyString())).thenReturn(SEOUL);

        ComplexGeocodingService.RunResult result = service(10_000, 2, 5, 2).run(Integer.MAX_VALUE);

        verify(transactionTemplate, times(3)).execute(any()); // 2 + 2 + 마지막 1
        assertThat(result.written()).isEqualTo(5);
    }

    @Test
    void 캐시_삭제가_실패해도_좌표_기록_결과는_그대로다() {
        target(1L, "서울특별시 종로구 내수동 72 경희궁의아침3단지", "내수동");
        when(client.geocode(anyString())).thenReturn(SEOUL);
        when(detailCache.evictIfPresent(1L)).thenThrow(new IllegalStateException("Redis 장애"));

        ComplexGeocodingService.RunResult result = service().run(Integer.MAX_VALUE);

        assertThat(result.written()).isEqualTo(1);
        assertThat(result.stopReason()).isEqualTo(StopReason.COMPLETED);
    }

    @Test
    void dry_run은_카카오를_호출하지_않고_정제_결과만_센다() {
        target(1L, "서울특별시 종로구 내수동 72 경희궁의아침3단지", "내수동");
        target(2L, "서울특별시 중랑구 망우동  신내역 힐데스하임아파트", "망우동");
        target(3L, "서울특별시 종로구 내수동 73 경희궁의아침4단지", "내수동");
        when(redisState.isMarkedNotFound(anyLong())).thenAnswer(invocation -> invocation.getArgument(0).equals(3L));

        ComplexGeocodingService.DryRunResult result = service().dryRun();

        verify(client, never()).geocode(anyString());
        verify(redisState, never()).recordCall();
        assertThat(result).isEqualTo(new ComplexGeocodingService.DryRunResult(3, 2, 1, 1));
    }
}
