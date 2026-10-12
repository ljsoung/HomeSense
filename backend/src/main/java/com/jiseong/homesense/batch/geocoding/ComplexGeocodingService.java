package com.jiseong.homesense.batch.geocoding;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import org.springframework.cache.Cache;
import org.springframework.cache.CacheManager;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;

import com.jiseong.homesense.common.cache.CacheNames;
import com.jiseong.homesense.common.config.GeocodingProperties;
import com.jiseong.homesense.common.logging.AuditLogger;
import com.jiseong.homesense.complex.repository.ComplexRepository;

import lombok.extern.slf4j.Slf4j;

/**
 * BAT-GEO-01. 좌표가 없는 단지(complex.latitude IS NULL)의 legal_dong_address를 정제해 카카오 주소 검색으로 좌표를 얻고
 * latitude/longitude/location_precision을 채운다. 일회성 백필 러너({@code backfill-geocoding} 프로필)와 BAT-SCH-01 03:00
 * 런(적재 뒤, BAT-NTF-01 앞)이 같은 {@link #run(int)}을 쓴다.
 *
 * <p>설계서 4.8절은 "도로명 우선, 실패 시 지번"이지만 complex에서 road_address를 이미 제거했다(미사용 컬럼 제거 원칙) —
 * 지번 단일 경로다. 지번을 못 뽑는 단지(약 1.2%)는 호출하지 않고 좌표 NULL로 남는다(CLAUDE.md BAT-GEO-01 절).
 *
 * <p>트랜잭션은 이 클래스가 아니라 청크 쓰기({@link TransactionTemplate})에만 건다 — 카카오 호출 수만 건 동안 트랜잭션과
 * 커넥션을 붙잡지 않고, 중간에 멈춰도 이미 쓴 청크는 남는다(TradeRematchRunner에서 겪은 "전체를 한 트랜잭션" 실수를
 * 되풀이하지 않는다).
 */
@Slf4j
@Service
public class ComplexGeocodingService {

    static final String PRECISION_PRECISE = "PRECISE";
    private static final int SAMPLE_LIMIT = 15;

    public enum StopReason {
        /** 대상을 끝까지 처리했다. */
        COMPLETED,
        /** 호출자가 준 단지 수 상한에 닿았다(러너의 --limit). */
        MAX_COMPLEXES,
        /** 오늘(KST) 호출 상한에 닿았다. 남은 단지는 다음 실행으로 넘어간다. */
        DAILY_LIMIT,
        /** 재시도까지 실패한 단지가 연달아 나왔다(카카오 장애 의심). */
        CONSECUTIVE_TRANSIENT_FAILURES,
        /** 401·403 — 키·앱 설정 문제. */
        CONFIGURATION_ERROR,
        /** 대기 중 인터럽트됐다(종료 신호). */
        INTERRUPTED
    }

    /**
     * @param targets   좌표가 없는 단지 수
     * @param noJibun   지번을 못 뽑아 호출하지 않은 단지 수
     * @param skipped   "주소를 못 찾음" 표시가 남아 있어 건너뛴 단지 수
     * @param found     좌표를 얻은 단지 수
     * @param written   실제로 DB에 쓴 단지 수(그사이 다른 실행이 먼저 채웠으면 found보다 작다)
     * @param notFound  카카오가 주소를 못 찾은 단지 수
     * @param transientFailed 재시도까지 실패한 단지 수
     * @param calls     이번 실행에서 보낸 카카오 호출 수(재시도 포함)
     */
    public record RunResult(int targets, int noJibun, int skipped, int found, int written, int notFound,
            int transientFailed, int calls, StopReason stopReason) {
    }

    /** @param refinable 지번을 뽑아 호출할 수 있는 단지 수, @param markedNotFound 그중 "못 찾음" 표시로 건너뛸 단지 수 */
    public record DryRunResult(int targets, int refinable, int noJibun, int markedNotFound) {
    }

    private record Target(Long complexId, String address, String dongRi) {
    }

    private record Coordinate(Long complexId, BigDecimal latitude, BigDecimal longitude) {
    }

    private final ComplexRepository complexRepository;
    private final KakaoGeocodingClient client;
    private final GeocodingRedisState redisState;
    private final GeocodingProperties properties;
    private final TransactionTemplate transactionTemplate;
    private final CacheManager cacheManager;
    private final AuditLogger auditLogger;
    private final Clock clock;

    private long lastCallAtMillis = 0L;

    public ComplexGeocodingService(ComplexRepository complexRepository, KakaoGeocodingClient client,
            GeocodingRedisState redisState, GeocodingProperties properties, TransactionTemplate transactionTemplate,
            CacheManager cacheManager, AuditLogger auditLogger, Clock clock) {
        this.complexRepository = complexRepository;
        this.client = client;
        this.redisState = redisState;
        this.properties = properties;
        this.transactionTemplate = transactionTemplate;
        this.cacheManager = cacheManager;
        this.auditLogger = auditLogger;
        this.clock = clock;
    }

    /** 카카오를 호출하지 않고 정제 결과만 센다. "못 찾음" 표시 확인을 위해 Redis는 읽는다. */
    public DryRunResult dryRun() {
        List<Target> targets = loadTargets();
        int refinable = 0;
        int marked = 0;
        List<String> noJibunSamples = new ArrayList<>();
        List<String> refinedSamples = new ArrayList<>();
        for (Target t : targets) {
            Optional<String> query = GeocodingAddressRefiner.refine(t.address(), t.dongRi());
            if (query.isEmpty()) {
                if (noJibunSamples.size() < SAMPLE_LIMIT) {
                    noJibunSamples.add(t.complexId() + " addr=" + t.address());
                }
                continue;
            }
            refinable++;
            if (refinedSamples.size() < SAMPLE_LIMIT) {
                refinedSamples.add(t.complexId() + " " + t.address() + " → " + query.get());
            }
            if (redisState.isMarkedNotFound(t.complexId())) {
                marked++;
            }
        }
        DryRunResult result = new DryRunResult(targets.size(), refinable, targets.size() - refinable, marked);
        log.info("[geocoding dry-run] 대상 {}건, 정제 가능 {}건, 지번 없음 {}건, 그중 '못 찾음' 표시로 건너뛸 단지 {}건, "
                + "오늘 호출 {}건 / 상한 {}건", result.targets(), result.refinable(), result.noJibun(),
                result.markedNotFound(), redisState.todayCalls(), properties.dailyLimit());
        refinedSamples.forEach(line -> log.info("  정제 {}", line));
        noJibunSamples.forEach(line -> log.info("  지번 없음 {}", line));
        return result;
    }

    /**
     * 좌표 없는 단지를 처리한다.
     *
     * @param maxComplexes 이번 실행에서 카카오에 보낼 단지 수 상한(재시도는 한 단지로 센다). 제한 없으면 {@link Integer#MAX_VALUE}
     */
    public RunResult run(int maxComplexes) {
        List<Target> targets = loadTargets();
        Counter counter = new Counter();
        List<Coordinate> pending = new ArrayList<>();
        StopReason stopReason = StopReason.COMPLETED;
        int consecutiveTransient = 0;
        int attempted = 0;
        try {
            for (Target t : targets) {
                Optional<String> query = GeocodingAddressRefiner.refine(t.address(), t.dongRi());
                if (query.isEmpty()) {
                    counter.noJibun++;
                    continue;
                }
                if (redisState.isMarkedNotFound(t.complexId())) {
                    counter.skipped++;
                    continue;
                }
                if (attempted >= maxComplexes) {
                    stopReason = StopReason.MAX_COMPLEXES;
                    break;
                }
                attempted++;

                KakaoGeocodingClient.GeocodeResult result;
                try {
                    result = callWithRetry(query.get(), counter);
                } catch (DailyLimitReachedException e) {
                    stopReason = StopReason.DAILY_LIMIT;
                    break;
                } catch (GeocodingTransientException e) {
                    counter.transientFailed++;
                    auditLogger.logGeocodingFailure(t.complexId(), query.get(), e.getMessage(), true);
                    if (++consecutiveTransient >= properties.maxConsecutiveTransientFailures()) {
                        stopReason = StopReason.CONSECUTIVE_TRANSIENT_FAILURES;
                        break;
                    }
                    continue;
                }
                consecutiveTransient = 0;

                switch (result) {
                    case KakaoGeocodingClient.Found found -> {
                        counter.found++;
                        pending.add(new Coordinate(t.complexId(), found.latitude(), found.longitude()));
                        if (pending.size() >= properties.chunkSize()) {
                            counter.written += flush(pending);
                        }
                    }
                    case KakaoGeocodingClient.NotFound notFound -> {
                        counter.notFound++;
                        redisState.markNotFound(t.complexId(), notFound.reason());
                        auditLogger.logGeocodingFailure(t.complexId(), query.get(), notFound.reason(), false);
                    }
                }
            }
        } catch (GeocodingConfigurationException e) {
            stopReason = StopReason.CONFIGURATION_ERROR;
            auditLogger.logBatchFailure("BAT-GEO-01", e);
        } catch (GeocodingInterruptedException e) {
            stopReason = StopReason.INTERRUPTED;
            Thread.currentThread().interrupt();
        } finally {
            // 어떤 경로로 끝나든 이미 받은 좌표는 쓴다 — 다시 받으려면 쿼터를 또 써야 한다.
            counter.written += flush(pending);
        }

        RunResult runResult = new RunResult(targets.size(), counter.noJibun, counter.skipped, counter.found,
                counter.written, counter.notFound, counter.transientFailed, counter.calls, stopReason);
        log.atInfo()
                .addKeyValue("programId", "BAT-GEO-01")
                .addKeyValue("targets", runResult.targets())
                .addKeyValue("found", runResult.found())
                .addKeyValue("written", runResult.written())
                .addKeyValue("notFound", runResult.notFound())
                .addKeyValue("noJibun", runResult.noJibun())
                .addKeyValue("skipped", runResult.skipped())
                .addKeyValue("transientFailed", runResult.transientFailed())
                .addKeyValue("calls", runResult.calls())
                .addKeyValue("stopReason", runResult.stopReason())
                .log("BAT-GEO-01 지오코딩 종료: 대상 {}건, 좌표 {}건(기록 {}건), 못 찾음 {}건, 지번 없음 {}건, 건너뜀 {}건, "
                        + "일시 실패 {}건, 호출 {}건, 종료 사유 {}", runResult.targets(), runResult.found(),
                        runResult.written(), runResult.notFound(), runResult.noJibun(), runResult.skipped(),
                        runResult.transientFailed(), runResult.calls(), runResult.stopReason());
        return runResult;
    }

    private KakaoGeocodingClient.GeocodeResult callWithRetry(String query, Counter counter) {
        for (int attempt = 0; ; attempt++) {
            reserveCall();
            counter.calls++;
            pace();
            try {
                return client.geocode(query);
            } catch (GeocodingTransientException e) {
                if (attempt >= properties.maxRetries()) {
                    throw e;
                }
                sleep(properties.retryBackoffMillis() * (attempt + 1));
            }
        }
    }

    /** 호출 하나를 오늘 카운터에 먼저 예약한다. 상한을 넘으면 예약을 되돌리고 멈춘다(동시 실행에도 상한을 넘지 않게 INCR로 센다). */
    private void reserveCall() {
        long count = redisState.recordCall();
        if (count > properties.dailyLimit()) {
            redisState.releaseCall();
            throw new DailyLimitReachedException();
        }
    }

    private void pace() {
        long remaining = properties.requestIntervalMillis() - (System.currentTimeMillis() - lastCallAtMillis);
        if (remaining > 0) {
            sleep(remaining);
        }
        lastCallAtMillis = System.currentTimeMillis();
    }

    private void sleep(long millis) {
        if (millis <= 0) {
            return;
        }
        try {
            Thread.sleep(millis);
        } catch (InterruptedException e) {
            throw new GeocodingInterruptedException();
        }
    }

    /**
     * 좌표를 청크로 커밋한 뒤 그 단지들의 상세 캐시를 지운다 — ComplexDetailResponse가 latitude/longitude/locationPrecision을
     * 담는다(complexDetailV3). 인기 단지 응답(ComplexSummaryResponse)에는 좌표가 없어 지우지 않는다. 일회성 러너는 곧바로
     * System.exit하므로 비동기인 evict() 대신 즉시 실행되는 evictIfPresent()를 쓴다(CLAUDE.md 캐싱 절).
     */
    private int flush(List<Coordinate> pending) {
        if (pending.isEmpty()) {
            return 0;
        }
        List<Coordinate> chunk = List.copyOf(pending);
        pending.clear();
        LocalDateTime now = LocalDateTime.now(clock);
        Integer written = transactionTemplate.execute(status -> chunk.stream()
                .mapToInt(c -> complexRepository.fillCoordinatesIfNull(c.complexId(), c.latitude(), c.longitude(),
                        PRECISION_PRECISE, now))
                .sum());
        evictDetailCache(chunk);
        return written == null ? 0 : written;
    }

    /** 캐시 삭제 실패는 이미 커밋된 좌표를 무효로 만들 이유가 없어 흡수한다 — 최악의 경우 TTL(24h) 동안 좌표 없는 상세가 보인다. */
    private void evictDetailCache(List<Coordinate> chunk) {
        try {
            Cache cache = cacheManager.getCache(CacheNames.COMPLEX_DETAIL);
            if (cache == null) {
                return;
            }
            for (Coordinate c : chunk) {
                cache.evictIfPresent(c.complexId());
            }
        } catch (RuntimeException e) {
            log.warn("[BAT-GEO-01] 단지 상세 캐시 삭제 실패 — 좌표는 커밋됨, 캐시는 TTL로 만료된다", e);
        }
    }

    private List<Target> loadTargets() {
        return complexRepository.findGeocodingTargets().stream()
                .map(r -> new Target(((Number) r[0]).longValue(), (String) r[1], (String) r[2]))
                .toList();
    }

    private static final class Counter {
        int noJibun;
        int skipped;
        int found;
        int written;
        int notFound;
        int transientFailed;
        int calls;
    }

    private static final class DailyLimitReachedException extends RuntimeException {
    }

    private static final class GeocodingInterruptedException extends RuntimeException {
    }
}
