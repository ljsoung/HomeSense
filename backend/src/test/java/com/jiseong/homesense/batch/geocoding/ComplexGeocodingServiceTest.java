package com.jiseong.homesense.batch.geocoding;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doReturn;
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
        lenient().when(client.searchKeyword(anyString(), anyInt())).thenReturn(lastPage());
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

    /** [complexId, legalDongAddress, dongRi, sido, sigungu, complexName] — 단지명은 "단지{id}"로 둔다. */
    private void target(long complexId, String address, String dongRi) {
        targets.add(new Object[] {complexId, address, dongRi, "서울특별시", "종로구", "단지" + complexId});
    }

    private void target(long complexId, String address, String dongRi, String sigungu, String complexName) {
        targets.add(new Object[] {complexId, address, dongRi, "서울특별시", sigungu, complexName});
    }

    /** 키워드 검색 마지막 페이지(is_end=true) 하나. */
    private static KakaoGeocodingClient.KeywordPage lastPage(KakaoGeocodingClient.KeywordPlace... places) {
        return new KakaoGeocodingClient.KeywordPage(List.of(places), true);
    }

    private static KakaoGeocodingClient.KeywordPlace apartment(String name, String address) {
        return new KakaoGeocodingClient.KeywordPlace(name, "부동산 > 주거시설 > 아파트", address,
                new BigDecimal("37.5014784"), new BigDecimal("127.0617312"));
    }

    @Test
    void 좌표를_얻으면_PRECISE로_쓰고_단지_상세_캐시를_지운다() {
        target(1L, "서울특별시 종로구 내수동 73- 경희궁의아침4단지", "내수동");
        when(client.geocode("서울특별시 종로구 내수동 73", false)).thenReturn(SEOUL);

        ComplexGeocodingService.RunResult result = service().run(Integer.MAX_VALUE);

        verify(complexRepository).fillCoordinatesIfNull(1L, SEOUL.latitude(), SEOUL.longitude(), "PRECISE",
                LocalDateTime.now(CLOCK));
        verify(detailCache).evictIfPresent(1L);
        assertThat(result).isEqualTo(
                new ComplexGeocodingService.RunResult(1, 0, 0, 1, 0, 1, 0, 0, 1, StopReason.COMPLETED));
    }

    @Test
    void 지번이_없거나_못_찾음_표시가_있는_단지는_호출하지_않는다() {
        target(1L, "서울특별시 중랑구 망우동  신내역 힐데스하임아파트", "망우동");
        target(2L, "서울특별시 종로구 내수동 72 경희궁의아침3단지", "내수동");
        when(redisState.isMarkedNotFound(2L)).thenReturn(true);

        ComplexGeocodingService.RunResult result = service().run(Integer.MAX_VALUE);

        verify(client, never()).geocode(anyString(), anyBoolean());
        assertThat(result.noJibun()).isEqualTo(1);
        assertThat(result.skipped()).isEqualTo(1);
        assertThat(result.calls()).isZero();
    }

    @Test
    void 주소_검색과_키워드_검색_모두_못_찾으면_건너뛸_표시를_남기고_재시도하지_않는다() {
        target(1L, "서울특별시 종로구 내수동 72 경희궁의아침3단지", "내수동");
        when(client.geocode(anyString(), anyBoolean())).thenReturn(new KakaoGeocodingClient.NotFound("NO_RESULT"));

        ComplexGeocodingService.RunResult result = service().run(Integer.MAX_VALUE);

        verify(client, times(1)).geocode(anyString(), anyBoolean());
        verify(client, times(1)).searchKeyword("종로구 단지1", 1);
        verify(redisState).markNotFound(1L, "NO_RESULT/KEYWORD_NO_CANDIDATE");
        verify(auditLogger).logGeocodingFailure(1L, "서울특별시 종로구 내수동 72", "NO_RESULT/KEYWORD_NO_CANDIDATE", false);
        assertThat(result.calls()).isEqualTo(2);
        verify(complexRepository, never()).fillCoordinatesIfNull(anyLong(), any(), any(), anyString(), any());
        assertThat(result.notFound()).isEqualTo(1);
    }

    @Test
    void 서버_오류는_백오프_후_재시도하고_끝내_실패하면_표시_없이_다음_실행으로_넘긴다() {
        target(1L, "서울특별시 종로구 내수동 72 경희궁의아침3단지", "내수동");
        target(2L, "서울특별시 종로구 내수동 73 경희궁의아침4단지", "내수동");
        when(client.geocode("서울특별시 종로구 내수동 72", false)).thenThrow(new GeocodingTransientException("HTTP_500"));
        when(client.geocode("서울특별시 종로구 내수동 73", false))
                .thenThrow(new GeocodingTransientException("HTTP_503"))
                .thenReturn(SEOUL);

        ComplexGeocodingService.RunResult result = service().run(Integer.MAX_VALUE);

        verify(client, times(3)).geocode("서울특별시 종로구 내수동 72", false); // 처음 + 재시도 2회
        verify(redisState, never()).markNotFound(eq(1L), anyString());
        verify(auditLogger).logGeocodingFailure(1L, "서울특별시 종로구 내수동 72", "HTTP_500", true);
        assertThat(result.transientFailed()).isEqualTo(1);
        assertThat(result.found()).isEqualTo(1);
        assertThat(result.calls()).isEqualTo(5);
        // 오류 응답을 받은 시도도 하나씩 센다 — 72는 3회, 73은 2회
        verify(redisState, times(5)).recordCall();
        assertThat(result.stopReason()).isEqualTo(StopReason.COMPLETED);
    }

    @Test
    void 일시_실패가_연달아_나오면_그_실행을_멈춘다() {
        for (long id = 1; id <= 5; id++) {
            target(id, "서울특별시 종로구 내수동 " + id + " 단지", "내수동");
        }
        when(client.geocode(anyString(), anyBoolean())).thenThrow(new GeocodingTransientException("HTTP_502"));

        ComplexGeocodingService.RunResult result = service(10_000, 0, 3, 100).run(Integer.MAX_VALUE);

        verify(client, times(3)).geocode(anyString(), anyBoolean());
        assertThat(result.stopReason()).isEqualTo(StopReason.CONSECUTIVE_TRANSIENT_FAILURES);
    }

    @Test
    void retry_failed도_일시_실패가_연달아_나오면_멈추고_못_찾음_표시를_건드리지_않는다() {
        for (long id = 1; id <= 5; id++) {
            target(id, "서울특별시 종로구 내수동 " + id + " 단지", "내수동");
        }
        when(redisState.isMarkedNotFound(anyLong())).thenReturn(true);
        when(client.geocode(anyString(), anyBoolean())).thenThrow(new GeocodingTransientException("HTTP_502"));

        ComplexGeocodingService.RunResult result = service(10_000, 0, 3, 100).run(Integer.MAX_VALUE, true);

        verify(client, times(3)).geocode(anyString(), anyBoolean());
        verify(redisState, never()).markNotFound(anyLong(), anyString());
        verify(redisState, never()).clearNotFound(anyLong());
        assertThat(result.transientFailed()).isEqualTo(3);
        assertThat(result.stopReason()).isEqualTo(StopReason.CONSECUTIVE_TRANSIENT_FAILURES);
    }

    @Test
    void 연속_일시_실패는_카카오가_정상_응답한_단지마다_다시_센다() {
        // 기준 3. 정상 응답 6종(PRECISE, 키워드 채택, 후보 없음, 이름 불일치, 후보 여럿, 결과 미완) 사이에 실패를 2번씩 끼운다.
        // 정상 응답이 하나라도 카운터를 초기화하지 않으면 그 뒤에서 3번째 실패가 되어 멈춘다.
        for (long id = 1; id <= 20; id++) {
            target(id, "서울특별시 종로구 내수동 " + id + " 단지" + id, "내수동");
        }
        when(client.geocode(anyString(), anyBoolean())).thenThrow(new GeocodingTransientException("HTTP_502"));
        doReturn(SEOUL).when(client).geocode("서울특별시 종로구 내수동 3", false);
        KakaoGeocodingClient.NotFound noResult = new KakaoGeocodingClient.NotFound("NO_RESULT");
        for (long id : new long[] {6, 9, 12, 15, 18}) {
            doReturn(noResult).when(client).geocode("서울특별시 종로구 내수동 " + id, false);
        }
        // 6: 후보 없음(기본 빈 페이지)
        when(client.searchKeyword("종로구 단지9", 1)).thenReturn(lastPage(apartment("다른아파트", "서울 종로구 내수동 75")));
        when(client.searchKeyword("종로구 단지12", 1)).thenReturn(lastPage(
                apartment("단지12A아파트", "서울 종로구 내수동 75"), apartment("단지12B아파트", "서울 종로구 내수동 76")));
        when(client.searchKeyword(eq("종로구 단지15"), anyInt())).thenReturn(page(apartment("다른아파트", "서울 종로구 내수동 75")));
        when(client.searchKeyword("종로구 단지18", 1)).thenReturn(lastPage(apartment("단지18아파트", "서울 종로구 내수동 75")));

        ComplexGeocodingService.RunResult result = service(10_000, 0, 3, 100).run(Integer.MAX_VALUE);

        assertThat(result.stopReason()).isEqualTo(StopReason.COMPLETED);
        assertThat(result.transientFailed()).isEqualTo(14);
        assertThat(result.found()).isEqualTo(2); // PRECISE 1 + 키워드 1
        verify(redisState).markNotFound(6L, "NO_RESULT/KEYWORD_NO_CANDIDATE");
        verify(redisState).markNotFound(9L, "NO_RESULT/KEYWORD_NAME_MISMATCH");
        verify(redisState).markNotFound(12L, "NO_RESULT/KEYWORD_AMBIGUOUS");
        verify(redisState).markNotFound(15L, "NO_RESULT/KEYWORD_INCOMPLETE");
    }

    @Test
    void 키워드_검색의_한_페이지가_재시도_끝에_실패해도_그_단지는_일시_실패_1회로_센다() {
        // 기준 2, 재시도 1회. 단지1은 1쪽은 정상이고 2쪽이 두 번 모두 5xx다 — 호출 단위로 세면 2를 넘어 멈춘다.
        target(1L, "서울특별시 종로구 내수동 1 단지1", "내수동");
        target(2L, "서울특별시 종로구 내수동 2 단지2", "내수동");
        when(client.geocode("서울특별시 종로구 내수동 1", false)).thenReturn(new KakaoGeocodingClient.NotFound("NO_RESULT"));
        when(client.searchKeyword("종로구 단지1", 1)).thenReturn(page());
        when(client.searchKeyword("종로구 단지1", 2)).thenThrow(new GeocodingTransientException("HTTP_503"));
        when(client.geocode("서울특별시 종로구 내수동 2", false)).thenReturn(SEOUL);

        ComplexGeocodingService.RunResult result = service(10_000, 1, 2, 100).run(Integer.MAX_VALUE);

        verify(client, times(2)).searchKeyword("종로구 단지1", 2); // 재시도까지 실패
        verify(redisState, never()).markNotFound(anyLong(), anyString()); // 판정하지 못한 단지는 표시하지 않는다
        assertThat(result.transientFailed()).isEqualTo(1);
        assertThat(result.found()).isEqualTo(1);
        assertThat(result.stopReason()).isEqualTo(StopReason.COMPLETED);
    }

    @Test
    void 일일_상한에_닿으면_보내지_않고_멈추며_받은_좌표는_쓴다() {
        target(1L, "서울특별시 종로구 내수동 72 경희궁의아침3단지", "내수동");
        target(2L, "서울특별시 종로구 내수동 73 경희궁의아침4단지", "내수동");
        todayCalls.set(9);
        when(client.geocode("서울특별시 종로구 내수동 72", false)).thenReturn(SEOUL);

        ComplexGeocodingService.RunResult result = service(10, 2, 5, 100).run(Integer.MAX_VALUE);

        verify(client, times(1)).geocode(anyString(), anyBoolean());
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
        when(client.geocode("서울특별시 종로구 내수동 72", false)).thenReturn(SEOUL);
        when(client.geocode("서울특별시 종로구 내수동 73", false)).thenThrow(new GeocodingConfigurationException("401"));

        ComplexGeocodingService.RunResult result = service().run(Integer.MAX_VALUE);

        verify(client, never()).geocode("서울특별시 종로구 내수동 74", false);
        verify(auditLogger).logBatchFailure(eq("BAT-GEO-01"), any(GeocodingConfigurationException.class));
        assertThat(result.stopReason()).isEqualTo(StopReason.CONFIGURATION_ERROR);
        assertThat(result.written()).isEqualTo(1);
    }

    @Test
    void 단지_수_상한이_있으면_그만큼만_보낸다() {
        target(1L, "서울특별시 종로구 내수동 72 경희궁의아침3단지", "내수동");
        target(2L, "서울특별시 중랑구 망우동  신내역 힐데스하임아파트", "망우동");
        target(3L, "서울특별시 종로구 내수동 73 경희궁의아침4단지", "내수동");
        when(client.geocode(anyString(), anyBoolean())).thenReturn(SEOUL);

        ComplexGeocodingService.RunResult result = service().run(1);

        verify(client, times(1)).geocode(anyString(), anyBoolean());
        assertThat(result.stopReason()).isEqualTo(StopReason.MAX_COMPLEXES);
        assertThat(result.noJibun()).isEqualTo(1);
    }

    @Test
    void 청크_크기마다_따로_커밋한다() {
        for (long id = 1; id <= 5; id++) {
            target(id, "서울특별시 종로구 내수동 " + id + " 단지", "내수동");
        }
        when(client.geocode(anyString(), anyBoolean())).thenReturn(SEOUL);

        ComplexGeocodingService.RunResult result = service(10_000, 2, 5, 2).run(Integer.MAX_VALUE);

        verify(transactionTemplate, times(3)).execute(any()); // 2 + 2 + 마지막 1
        assertThat(result.written()).isEqualTo(5);
    }

    @Test
    void 캐시_삭제가_실패해도_좌표_기록_결과는_그대로다() {
        target(1L, "서울특별시 종로구 내수동 72 경희궁의아침3단지", "내수동");
        when(client.geocode(anyString(), anyBoolean())).thenReturn(SEOUL);
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

        verify(client, never()).geocode(anyString(), anyBoolean());
        verify(redisState, never()).recordCall();
        assertThat(result).isEqualTo(new ComplexGeocodingService.DryRunResult(3, 2, 1, 1));
    }

    @Test
    void 요청_한도_429를_받으면_재시도하지_않고_그_실행을_즉시_멈춘다() {
        target(1L, "서울특별시 종로구 내수동 72 경희궁의아침3단지", "내수동");
        target(2L, "서울특별시 종로구 내수동 73 경희궁의아침4단지", "내수동");
        target(3L, "서울특별시 종로구 내수동 74 경희궁의아침5단지", "내수동");
        when(client.geocode("서울특별시 종로구 내수동 72", false)).thenReturn(SEOUL);
        when(client.geocode("서울특별시 종로구 내수동 73", false))
                .thenThrow(new GeocodingRateLimitedException("429"));

        ComplexGeocodingService.RunResult result = service().run(Integer.MAX_VALUE);

        verify(client, times(1)).geocode("서울특별시 종로구 내수동 73", false); // 재시도 없음
        verify(client, never()).geocode("서울특별시 종로구 내수동 74", false);
        verify(redisState, times(2)).recordCall(); // 429 응답을 받은 시도도 센다
        verify(redisState, never()).markNotFound(eq(2L), anyString());
        verify(auditLogger).logBatchFailure(eq("BAT-GEO-01"), any(GeocodingRateLimitedException.class));
        assertThat(result.stopReason()).isEqualTo(StopReason.RATE_LIMITED);
        assertThat(result.written()).isEqualTo(1);
    }

    @Test
    void 권한_없음_403을_받으면_재시도하지_않고_그_실행을_즉시_멈춘다() {
        target(1L, "서울특별시 종로구 내수동 72 경희궁의아침3단지", "내수동");
        target(2L, "서울특별시 종로구 내수동 73 경희궁의아침4단지", "내수동");
        when(client.geocode("서울특별시 종로구 내수동 72", false)).thenThrow(new GeocodingConfigurationException("403"));

        ComplexGeocodingService.RunResult result = service().run(Integer.MAX_VALUE);

        verify(client, times(1)).geocode(anyString(), anyBoolean());
        verify(redisState, times(1)).recordCall();
        assertThat(result.stopReason()).isEqualTo(StopReason.CONFIGURATION_ERROR);
    }

    @Test
    void 동리가_없는_단지는_결과가_하나일_때만_좌표를_쓰도록_요청한다() {
        target(1L, "전남광주통합특별시 광양시 광양읍  484-28 광양목성파크뷰 부영2단지", null);
        when(client.geocode("전남광주통합특별시 광양시 광양읍 484-28", true)).thenReturn(SEOUL);

        ComplexGeocodingService.RunResult result = service().run(Integer.MAX_VALUE);

        assertThat(result.written()).isEqualTo(1);
        assertThat(result.noJibun()).isZero();
    }

    // 키워드 보완 경로

    @Test
    void 주소_검색이_못_찾으면_키워드_검색으로_찾아_APPROXIMATE로_쓴다() {
        target(1L, "서울특별시 강남구 대치동 963 대치푸르지오써밋", "대치동", "강남구", "대치푸르지오써밋");
        when(client.geocode("서울특별시 강남구 대치동 963", false)).thenReturn(new KakaoGeocodingClient.NotFound("NO_RESULT"));
        KakaoGeocodingClient.KeywordPlace place = apartment("대치푸르지오써밋아파트", "서울 강남구 대치동 1032");
        when(client.searchKeyword("강남구 대치푸르지오써밋", 1)).thenReturn(lastPage(place));

        ComplexGeocodingService.RunResult result = service().run(Integer.MAX_VALUE);

        verify(complexRepository).fillCoordinatesIfNull(1L, place.latitude(), place.longitude(), "APPROXIMATE",
                LocalDateTime.now(CLOCK));
        verify(redisState, never()).markNotFound(anyLong(), anyString());
        verify(redisState, times(2)).recordCall(); // 주소 검색 1 + 키워드 검색 1
        assertThat(result.found()).isEqualTo(1);
        assertThat(result.foundByKeyword()).isEqualTo(1);
        assertThat(result.calls()).isEqualTo(2);
    }

    @Test
    void 키워드_후보가_없으면_단지명_끝의_아파트를_떼고_한_번_더_찾는다() {
        target(1L, "서울특별시 종로구 내수동 72 경희궁아파트", "내수동", "종로구", "경희궁아파트");
        when(client.geocode(anyString(), anyBoolean())).thenReturn(new KakaoGeocodingClient.NotFound("NO_RESULT"));
        when(client.searchKeyword("종로구 경희궁아파트", 1)).thenReturn(lastPage());
        when(client.searchKeyword("종로구 경희궁", 1))
                .thenReturn(lastPage(apartment("경희궁아파트", "서울 종로구 내수동 75")));

        ComplexGeocodingService.RunResult result = service().run(Integer.MAX_VALUE);

        assertThat(result.foundByKeyword()).isEqualTo(1);
        assertThat(result.calls()).isEqualTo(3);
    }

    @Test
    void 이름을_포함하는_키워드_후보가_여럿이면_다시_찾지_않고_못_찾음으로_남긴다() {
        target(1L, "서울특별시 종로구 내수동 72 경희궁아파트", "내수동", "종로구", "경희궁아파트");
        when(client.geocode(anyString(), anyBoolean())).thenReturn(new KakaoGeocodingClient.NotFound("NO_RESULT"));
        when(client.searchKeyword("종로구 경희궁아파트", 1)).thenReturn(lastPage(
                apartment("경희궁1단지", "서울 종로구 내수동 75"), apartment("경희궁2단지", "서울 종로구 내수동 76")));

        ComplexGeocodingService.RunResult result = service().run(Integer.MAX_VALUE);

        verify(client, never()).searchKeyword("종로구 경희궁", 1);
        verify(redisState).markNotFound(1L, "NO_RESULT/KEYWORD_AMBIGUOUS");
        assertThat(result.notFound()).isEqualTo(1);
    }

    @Test
    void 키워드_검색에서_429를_받아도_그_실행을_즉시_멈춘다() {
        target(1L, "서울특별시 종로구 내수동 72 경희궁의아침3단지", "내수동");
        target(2L, "서울특별시 종로구 내수동 73 경희궁의아침4단지", "내수동");
        when(client.geocode(anyString(), anyBoolean())).thenReturn(new KakaoGeocodingClient.NotFound("NO_RESULT"));
        when(client.searchKeyword(anyString(), anyInt())).thenThrow(new GeocodingRateLimitedException("429"));

        ComplexGeocodingService.RunResult result = service().run(Integer.MAX_VALUE);

        verify(client, times(1)).searchKeyword(anyString(), anyInt()); // 재시도 없음
        verify(client, times(1)).geocode(anyString(), anyBoolean()); // 다음 단지로 가지 않음
        verify(redisState, never()).markNotFound(anyLong(), anyString());
        assertThat(result.stopReason()).isEqualTo(StopReason.RATE_LIMITED);
    }

    @Test
    void 키워드_검색의_5xx도_백오프_후_재시도하고_카운터를_센다() {
        target(1L, "서울특별시 강남구 대치동 963 대치푸르지오써밋", "대치동", "강남구", "대치푸르지오써밋");
        when(client.geocode(anyString(), anyBoolean())).thenReturn(new KakaoGeocodingClient.NotFound("NO_RESULT"));
        when(client.searchKeyword("강남구 대치푸르지오써밋", 1))
                .thenThrow(new GeocodingTransientException("HTTP_503"))
                .thenReturn(lastPage(apartment("대치푸르지오써밋아파트", "서울 강남구 대치동 1032")));

        ComplexGeocodingService.RunResult result = service().run(Integer.MAX_VALUE);

        verify(client, times(2)).searchKeyword("강남구 대치푸르지오써밋", 1);
        verify(redisState, times(3)).recordCall();
        assertThat(result.foundByKeyword()).isEqualTo(1);
    }

    @Test
    void 키워드_검색_중_일일_상한에_닿으면_멈추고_표시를_남기지_않는다() {
        target(1L, "서울특별시 강남구 대치동 963 대치푸르지오써밋", "대치동", "강남구", "대치푸르지오써밋");
        todayCalls.set(9);
        when(client.geocode(anyString(), anyBoolean())).thenReturn(new KakaoGeocodingClient.NotFound("NO_RESULT"));

        ComplexGeocodingService.RunResult result = service(10, 2, 5, 100).run(Integer.MAX_VALUE);

        verify(client, never()).searchKeyword(anyString(), anyInt());
        verify(redisState, never()).markNotFound(anyLong(), anyString());
        assertThat(result.stopReason()).isEqualTo(StopReason.DAILY_LIMIT);
    }

    @Test
    void retry_failed는_못_찾음_표시가_있는_단지만_처리하고_찾으면_표시를_지운다() {
        target(1L, "서울특별시 강남구 대치동 963 대치푸르지오써밋", "대치동", "강남구", "대치푸르지오써밋");
        target(2L, "서울특별시 종로구 내수동 72 경희궁의아침3단지", "내수동");
        target(3L, "서울특별시 중랑구 망우동  신내역 힐데스하임아파트", "망우동");
        when(redisState.isMarkedNotFound(anyLong())).thenAnswer(invocation -> invocation.getArgument(0).equals(1L));
        when(client.geocode("서울특별시 강남구 대치동 963", false)).thenReturn(new KakaoGeocodingClient.NotFound("NO_RESULT"));
        when(client.searchKeyword("강남구 대치푸르지오써밋", 1))
                .thenReturn(lastPage(apartment("대치푸르지오써밋아파트", "서울 강남구 대치동 1032")));

        ComplexGeocodingService.RunResult result = service().run(Integer.MAX_VALUE, true);

        verify(client, never()).geocode("서울특별시 종로구 내수동 72", false); // 표시 없는 단지는 대상이 아니다
        verify(redisState).clearNotFound(1L);
        assertThat(result.targets()).isEqualTo(1);
        assertThat(result.foundByKeyword()).isEqualTo(1);
        assertThat(result.noJibun()).isZero();
    }

    @Test
    void retry_failed에서_또_못_찾으면_새_사유로_표시를_다시_쓴다() {
        target(1L, "서울특별시 강남구 대치동 963 대치푸르지오써밋", "대치동", "강남구", "대치푸르지오써밋");
        when(redisState.isMarkedNotFound(1L)).thenReturn(true);
        when(client.geocode(anyString(), anyBoolean())).thenReturn(new KakaoGeocodingClient.NotFound("NO_RESULT"));

        service().run(Integer.MAX_VALUE, true);

        verify(redisState).markNotFound(1L, "NO_RESULT/KEYWORD_NO_CANDIDATE");
        verify(redisState, never()).clearNotFound(anyLong());
    }

    @Test
    void 이름이_맞지_않으면_다음_검색어로_다시_찾고_끝내_없으면_이름_불일치로_남긴다() {
        targets.add(new Object[] {1L, "경기도 화성동탄구 영천동 651-1372 동탄파크자이아파트", "영천동", "경기도", "화성동탄구",
                "동탄파크자이아파트"});
        when(client.geocode(anyString(), anyBoolean())).thenReturn(new KakaoGeocodingClient.NotFound("NO_RESULT"));
        when(client.searchKeyword(anyString(), anyInt())).thenReturn(lastPage(
                new KakaoGeocodingClient.KeywordPlace("동탄역자이아파트", "부동산 > 주거시설 > 아파트",
                        "경기 화성시 동탄구 영천동 892", new BigDecimal("37.2"), new BigDecimal("127.1"))));

        ComplexGeocodingService.RunResult result = service().run(Integer.MAX_VALUE);

        verify(client).searchKeyword("화성동탄구 동탄파크자이아파트", 1);
        verify(client).searchKeyword("화성동탄구 동탄파크자이", 1);
        verify(redisState).markNotFound(1L, "NO_RESULT/KEYWORD_NAME_MISMATCH");
        assertThat(result.foundByKeyword()).isZero();
    }

    // 키워드 검색 페이지

    private static KakaoGeocodingClient.KeywordPage page(KakaoGeocodingClient.KeywordPlace... places) {
        return new KakaoGeocodingClient.KeywordPage(List.of(places), false);
    }

    private void keywordTarget() {
        target(1L, "서울특별시 종로구 내수동 72 경희궁아파트", "내수동", "종로구", "경희궁아파트");
        when(client.geocode(anyString(), anyBoolean())).thenReturn(new KakaoGeocodingClient.NotFound("NO_RESULT"));
    }

    @Test
    void 첫_페이지에_없는_단지도_마지막_페이지까지_받아_찾는다() {
        keywordTarget();
        KakaoGeocodingClient.KeywordPlace place = apartment("경희궁아파트", "서울 종로구 내수동 75");
        when(client.searchKeyword("종로구 경희궁아파트", 1)).thenReturn(page());
        when(client.searchKeyword("종로구 경희궁아파트", 2)).thenReturn(lastPage(place));

        ComplexGeocodingService.RunResult result = service().run(Integer.MAX_VALUE);

        verify(complexRepository).fillCoordinatesIfNull(1L, place.latitude(), place.longitude(), "APPROXIMATE",
                LocalDateTime.now(CLOCK));
        verify(client, never()).searchKeyword(eq("종로구 경희궁"), anyInt()); // 첫 검색어에서 찾았다
        verify(redisState, times(3)).recordCall(); // 주소 1 + 키워드 2쪽
        assertThat(result.calls()).isEqualTo(3);
        assertThat(result.foundByKeyword()).isEqualTo(1);
    }

    @Test
    void 뒤_페이지에_이름을_포함하는_후보가_또_있으면_첫_페이지_후보를_채택하지_않는다() {
        keywordTarget();
        when(client.searchKeyword("종로구 경희궁아파트", 1)).thenReturn(page(apartment("경희궁1단지", "서울 종로구 내수동 75")));
        when(client.searchKeyword("종로구 경희궁아파트", 2)).thenReturn(lastPage(apartment("경희궁2단지", "서울 종로구 내수동 76")));

        ComplexGeocodingService.RunResult result = service().run(Integer.MAX_VALUE);

        verify(complexRepository, never()).fillCoordinatesIfNull(anyLong(), any(), any(), anyString(), any());
        verify(redisState).markNotFound(1L, "NO_RESULT/KEYWORD_AMBIGUOUS");
        assertThat(result.notFound()).isEqualTo(1);
    }

    @Test
    void 상한_페이지까지_받아도_끝나지_않으면_일부_결과로_채택하지_않고_멈춘다() {
        keywordTarget();
        when(client.searchKeyword(eq("종로구 경희궁아파트"), anyInt()))
                .thenReturn(page(apartment("경희궁아파트", "서울 종로구 내수동 75")));

        ComplexGeocodingService.RunResult result = service().run(Integer.MAX_VALUE);

        verify(client, times(KakaoGeocodingClient.KEYWORD_MAX_PAGES)).searchKeyword(eq("종로구 경희궁아파트"), anyInt());
        verify(client, never()).searchKeyword(eq("종로구 경희궁"), anyInt());
        verify(complexRepository, never()).fillCoordinatesIfNull(anyLong(), any(), any(), anyString(), any());
        verify(redisState).markNotFound(1L, "NO_RESULT/KEYWORD_INCOMPLETE");
        assertThat(result.calls()).isEqualTo(1 + KakaoGeocodingClient.KEYWORD_MAX_PAGES);
    }

    @Test
    void 페이지_사이에_같은_문서가_다시_와도_한_번만_센다() {
        keywordTarget();
        KakaoGeocodingClient.KeywordPlace place = apartment("경희궁아파트", "서울 종로구 내수동 75");
        when(client.searchKeyword("종로구 경희궁아파트", 1)).thenReturn(page(place));
        when(client.searchKeyword("종로구 경희궁아파트", 2)).thenReturn(lastPage(place));

        ComplexGeocodingService.RunResult result = service().run(Integer.MAX_VALUE);

        assertThat(result.foundByKeyword()).isEqualTo(1);
    }

    @Test
    void 뒤_페이지의_5xx는_그_페이지만_다시_보낸다() {
        keywordTarget();
        KakaoGeocodingClient.KeywordPlace place = apartment("경희궁아파트", "서울 종로구 내수동 75");
        when(client.searchKeyword("종로구 경희궁아파트", 1)).thenReturn(page());
        when(client.searchKeyword("종로구 경희궁아파트", 2))
                .thenThrow(new GeocodingTransientException("HTTP_503"))
                .thenReturn(lastPage(place));

        ComplexGeocodingService.RunResult result = service().run(Integer.MAX_VALUE);

        verify(client, times(1)).searchKeyword("종로구 경희궁아파트", 1);
        verify(client, times(2)).searchKeyword("종로구 경희궁아파트", 2);
        assertThat(result.calls()).isEqualTo(4); // 주소 1 + 1쪽 1 + 2쪽 2
        assertThat(result.foundByKeyword()).isEqualTo(1);
    }

    // APPROXIMATE 재판정

    private final List<Object[]> approximate = new ArrayList<>();

    private void approximate(long complexId, String sigungu, String dongRi, String complexName, String lat, String lng) {
        approximate.add(new Object[] {complexId, "주소", dongRi, "서울특별시", sigungu, complexName, new BigDecimal(lat),
                new BigDecimal(lng)});
    }

    @Test
    void 재판정에서_다시_채택되고_좌표가_같으면_그대로_둔다() {
        approximate(1L, "강남구", "대치동", "대치푸르지오써밋", "37.5014784", "127.0617312");
        when(complexRepository.findApproximateTargets()).thenReturn(approximate);
        when(client.searchKeyword("강남구 대치푸르지오써밋", 1))
                .thenReturn(lastPage(apartment("대치푸르지오써밋아파트", "서울 강남구 대치동 1032")));

        ComplexGeocodingService.RecheckResult result = service().recheckApproximate(Integer.MAX_VALUE);

        verify(complexRepository, never()).updateApproximateCoordinates(anyLong(), any(), any(), any());
        verify(complexRepository, never()).clearApproximateCoordinates(anyLong(), any());
        assertThat(result.kept()).isEqualTo(1);
        verify(client, never()).geocode(anyString(), anyBoolean()); // 주소 검색은 다시 하지 않는다
    }

    @Test
    void 재판정에서_다른_장소가_골라지면_좌표를_바꾼다() {
        approximate(1L, "강남구", "대치동", "대치푸르지오써밋", "37.0000000", "127.0000000");
        when(complexRepository.findApproximateTargets()).thenReturn(approximate);
        KakaoGeocodingClient.KeywordPlace place = apartment("대치푸르지오써밋아파트", "서울 강남구 대치동 1032");
        when(client.searchKeyword("강남구 대치푸르지오써밋", 1)).thenReturn(lastPage(place));

        ComplexGeocodingService.RecheckResult result = service().recheckApproximate(Integer.MAX_VALUE);

        verify(complexRepository).updateApproximateCoordinates(1L, place.latitude(), place.longitude(),
                LocalDateTime.now(CLOCK));
        verify(detailCache).evictIfPresent(1L);
        assertThat(result.moved()).isEqualTo(1);
    }

    @Test
    void 재판정에서_탈락하면_좌표를_되돌리고_못_찾음_표시를_남긴다() {
        approximate(1L, "화성동탄구", "영천동", "동탄파크자이아파트", "37.2", "127.1");
        when(complexRepository.findApproximateTargets()).thenReturn(approximate);
        when(client.searchKeyword(anyString(), anyInt())).thenReturn(lastPage(
                new KakaoGeocodingClient.KeywordPlace("동탄역자이아파트",
                "부동산 > 주거시설 > 아파트", "서울 화성동탄구 영천동 892", new BigDecimal("37.2"), new BigDecimal("127.1"))));

        ComplexGeocodingService.RecheckResult result = service().recheckApproximate(Integer.MAX_VALUE);

        verify(complexRepository).clearApproximateCoordinates(1L, LocalDateTime.now(CLOCK));
        verify(redisState).markNotFound(1L, "RECHECK/KEYWORD_NAME_MISMATCH");
        verify(detailCache).evictIfPresent(1L);
        assertThat(result.dropped()).isEqualTo(1);
    }

    @Test
    void 재판정도_일시_실패가_연달아_나오면_멈추고_좌표를_건드리지_않는다() {
        for (long id = 1; id <= 5; id++) {
            approximate(id, "강남구", "대치동", "단지" + id, "37.5", "127.0");
        }
        when(complexRepository.findApproximateTargets()).thenReturn(approximate);
        when(client.searchKeyword(anyString(), anyInt())).thenThrow(new GeocodingTransientException("HTTP_502"));

        ComplexGeocodingService.RecheckResult result = service(10_000, 0, 3, 100).recheckApproximate(Integer.MAX_VALUE);

        verify(client, times(3)).searchKeyword(anyString(), anyInt()); // 4·5번째 단지는 보내지 않는다
        verify(complexRepository, never()).clearApproximateCoordinates(anyLong(), any());
        verify(complexRepository, never()).updateApproximateCoordinates(anyLong(), any(), any(), any());
        assertThat(result.transientFailed()).isEqualTo(3);
        assertThat(result.calls()).isEqualTo(3);
        assertThat(result.stopReason()).isEqualTo(StopReason.CONSECUTIVE_TRANSIENT_FAILURES);
    }

    @Test
    void 재판정의_연속_일시_실패는_판정에_성공한_단지에서_다시_센다() {
        approximate(1L, "강남구", "대치동", "단지1", "37.5", "127.0");
        approximate(2L, "강남구", "대치동", "단지2", "37.5", "127.0");
        approximate(3L, "강남구", "대치동", "대치푸르지오써밋", "37.5014784", "127.0617312");
        approximate(4L, "강남구", "대치동", "단지4", "37.5", "127.0");
        approximate(5L, "강남구", "대치동", "단지5", "37.5", "127.0");
        approximate(6L, "강남구", "대치동", "단지6", "37.5", "127.0");
        approximate(7L, "강남구", "대치동", "단지7", "37.5", "127.0");
        approximate(8L, "강남구", "대치동", "단지8", "37.5", "127.0");
        when(complexRepository.findApproximateTargets()).thenReturn(approximate);
        when(client.searchKeyword(anyString(), anyInt())).thenThrow(new GeocodingTransientException("HTTP_502"));
        doReturn(lastPage(apartment("대치푸르지오써밋아파트", "서울 강남구 대치동 1032")))
                .when(client).searchKeyword("강남구 대치푸르지오써밋", 1);
        doReturn(lastPage(apartment("다른아파트", "서울 강남구 대치동 1"))).when(client).searchKeyword("강남구 단지6", 1);

        ComplexGeocodingService.RecheckResult result = service(10_000, 0, 3, 100).recheckApproximate(Integer.MAX_VALUE);

        assertThat(result.kept()).isEqualTo(1); // 3번: 다시 채택
        assertThat(result.dropped()).isEqualTo(1); // 6번: 이름 불일치로 탈락해도 카카오는 정상 응답했다
        assertThat(result.transientFailed()).isEqualTo(6);
        assertThat(result.stopReason()).isEqualTo(StopReason.COMPLETED);
    }

    @Test
    void 재판정도_429를_받으면_즉시_멈추고_좌표를_건드리지_않는다() {
        approximate(1L, "강남구", "대치동", "대치푸르지오써밋", "37.5", "127.0");
        approximate(2L, "강남구", "대치동", "대치아이파크", "37.5", "127.0");
        when(complexRepository.findApproximateTargets()).thenReturn(approximate);
        when(client.searchKeyword(anyString(), anyInt())).thenThrow(new GeocodingRateLimitedException("429"));

        ComplexGeocodingService.RecheckResult result = service().recheckApproximate(Integer.MAX_VALUE);

        verify(client, times(1)).searchKeyword(anyString(), anyInt());
        verify(complexRepository, never()).clearApproximateCoordinates(anyLong(), any());
        assertThat(result.stopReason()).isEqualTo(StopReason.RATE_LIMITED);
    }
}
