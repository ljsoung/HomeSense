package com.jiseong.homesense.batch.geocoding;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.function.Supplier;

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
    /**
     * 키워드 장소 검색(단지명)으로 얻은 좌표. 이름으로 맞춘 결과라 다른 단지를 잘못 집었을 가능성이 남아 근사로 둔다 — UI정의서
     * 7.2절의 근사 정의와 같고, 상세·지도 화면은 PRECISE가 아니면 "근사 위치입니다"를 보인다. 11자라 컬럼을 VARCHAR(12)로 넓혔다
     * (schema/location_precision_varchar12.sql).
     */
    static final String PRECISION_APPROXIMATE = "APPROXIMATE";
    private static final String REASON_NO_CANDIDATE = "KEYWORD_NO_CANDIDATE";
    private static final String REASON_NAME_MISMATCH = "KEYWORD_NAME_MISMATCH";
    private static final String REASON_AMBIGUOUS = "KEYWORD_AMBIGUOUS";
    /** 결과가 상한 페이지 안에 끝나지 않아 일부만 받았다 — 채택하지 않는다. */
    private static final String REASON_INCOMPLETE = "KEYWORD_INCOMPLETE";
    private static final int SAMPLE_LIMIT = 15;

    public enum StopReason {
        /** 대상을 끝까지 처리했다. */
        COMPLETED,
        /** 호출자가 준 단지 수 상한에 닿았다(러너의 --limit). */
        MAX_COMPLEXES,
        /** 오늘(KST) 호출 상한에 닿았다. 남은 단지는 다음 실행으로 넘어간다. */
        DAILY_LIMIT,
        /** 재시도(5xx·네트워크 오류)까지 실패한 단지가 연달아 나왔다(카카오 장애 의심). */
        CONSECUTIVE_TRANSIENT_FAILURES,
        /** 401·403 — 키·앱 설정 문제. */
        CONFIGURATION_ERROR,
        /** 429 — 카카오 요청 한도. 재시도하지 않고 바로 멈춘다. */
        RATE_LIMITED,
        /** 대기 중 인터럽트됐다(종료 신호). */
        INTERRUPTED
    }

    /**
     * @param targets   좌표가 없는 단지 수
     * @param noJibun   지번을 못 뽑아 호출하지 않은 단지 수
     * @param skipped   "주소를 못 찾음" 표시가 남아 있어 건너뛴 단지 수
     * @param found     좌표를 얻은 단지 수(주소 검색 + 키워드 보완)
     * @param foundByKeyword 그중 키워드 장소 검색으로 얻어 APPROXIMATE로 쓴 단지 수
     * @param written   실제로 DB에 쓴 단지 수(그사이 다른 실행이 먼저 채웠으면 found보다 작다)
     * @param notFound  카카오가 주소를 못 찾은 단지 수
     * @param transientFailed 재시도까지 실패한 단지 수
     * @param calls     이번 실행에서 보낸 카카오 호출 수(재시도 포함)
     */
    public record RunResult(int targets, int noJibun, int skipped, int found, int foundByKeyword, int written,
            int notFound, int transientFailed, int calls, StopReason stopReason) {
    }

    /**
     * @param kept    다시 채택돼 좌표가 그대로인 단지 수
     * @param moved   다시 채택됐지만 다른 장소가 골라져 좌표를 바꾼 단지 수
     * @param dropped 탈락해 좌표를 NULL로 되돌리고 "못 찾음" 표시를 남긴 단지 수
     */
    public record RecheckResult(int targets, int kept, int moved, int dropped, int transientFailed, int calls,
            StopReason stopReason) {
    }

    /** @param refinable 지번을 뽑아 호출할 수 있는 단지 수, @param markedNotFound 그중 "못 찾음" 표시로 건너뛸 단지 수 */
    public record DryRunResult(int targets, int refinable, int noJibun, int markedNotFound) {
    }

    private record Target(Long complexId, String address, String dongRi, String sido, String sigungu,
            String complexName) {

        KeywordPlaceMatcher.ComplexIdentity identity() {
            return new KeywordPlaceMatcher.ComplexIdentity(sido, sigungu, dongRi, complexName);
        }
    }

    private record Coordinate(Long complexId, BigDecimal latitude, BigDecimal longitude, String precision) {
    }

    private record ApproximateTarget(Target target, BigDecimal latitude, BigDecimal longitude) {
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
            Optional<GeocodingAddressRefiner.RefinedAddress> refined = GeocodingAddressRefiner.refine(t.address(), t.dongRi());
            if (refined.isEmpty()) {
                if (noJibunSamples.size() < SAMPLE_LIMIT) {
                    noJibunSamples.add(t.complexId() + " addr=" + t.address());
                }
                continue;
            }
            refinable++;
            if (refinedSamples.size() < SAMPLE_LIMIT) {
                refinedSamples.add(t.complexId() + " " + t.address() + " → " + refined.get().query()
                        + (refined.get().requireUnique() ? " (유일한 결과만)" : ""));
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

    /** 좌표 없는 단지 중 "못 찾음" 표시가 없는 단지를 처리한다(정기 런·러너 기본). */
    public RunResult run(int maxComplexes) {
        return run(maxComplexes, false);
    }

    /**
     * 좌표 없는 단지를 처리한다. 단지마다 주소 검색을 먼저 하고, 못 찾으면 키워드 장소 검색으로 한 번 더 찾는다(정기 런과 러너가
     * 같은 순서).
     *
     * @param maxComplexes 이번 실행에서 카카오에 보낼 단지 수 상한(재시도·키워드 검색도 한 단지로 센다). 제한 없으면
     *                     {@link Integer#MAX_VALUE}
     * @param retryFailed  true면 "못 찾음" 표시({@code geo:fail})가 남아 있는 단지<b>만</b> 다시 처리한다(러너의
     *                     {@code --retry-failed}). 표시를 수동으로 지우지 않고 이전 실패를 다시 돌리는 경로다. 좌표를 얻으면 표시를
     *                     지우고, 또 못 찾으면 표시를 새 사유와 TTL로 다시 쓴다.
     */
    public RunResult run(int maxComplexes, boolean retryFailed) {
        List<Target> targets = loadTargets();
        if (retryFailed) {
            targets = targets.stream()
                    .filter(t -> GeocodingAddressRefiner.refine(t.address(), t.dongRi()).isPresent())
                    .filter(t -> redisState.isMarkedNotFound(t.complexId()))
                    .toList();
        }
        Counter counter = new Counter();
        List<Coordinate> pending = new ArrayList<>();
        StopReason stopReason = StopReason.COMPLETED;
        TransientFailureStreak transientStreak = new TransientFailureStreak(properties.maxConsecutiveTransientFailures());
        int attempted = 0;
        try {
            for (Target t : targets) {
                Optional<GeocodingAddressRefiner.RefinedAddress> refined = GeocodingAddressRefiner.refine(t.address(), t.dongRi());
                if (refined.isEmpty()) {
                    counter.noJibun++;
                    continue;
                }
                if (!retryFailed && redisState.isMarkedNotFound(t.complexId())) {
                    counter.skipped++;
                    continue;
                }
                if (attempted >= maxComplexes) {
                    stopReason = StopReason.MAX_COMPLEXES;
                    break;
                }
                attempted++;

                Coordinate coordinate;
                String notFoundReason = null;
                try {
                    GeocodingAddressRefiner.RefinedAddress address = refined.get();
                    KakaoGeocodingClient.GeocodeResult result = callWithRetry(
                            () -> client.geocode(address.query(), address.requireUnique()), counter);
                    switch (result) {
                        case KakaoGeocodingClient.Found found -> coordinate = new Coordinate(t.complexId(),
                                found.latitude(), found.longitude(), PRECISION_PRECISE);
                        case KakaoGeocodingClient.NotFound notFound -> {
                            KeywordPlaceMatcher.Result keyword = searchByKeyword(t, counter);
                            if (keyword instanceof KeywordPlaceMatcher.Matched matched) {
                                coordinate = new Coordinate(t.complexId(), matched.place().latitude(),
                                        matched.place().longitude(), PRECISION_APPROXIMATE);
                                logKeywordMatch(t, matched.place());
                            } else {
                                coordinate = null;
                                notFoundReason = notFound.reason() + "/" + ((KeywordPlaceMatcher.NoMatch) keyword).reason();
                            }
                        }
                    }
                } catch (DailyLimitReachedException e) {
                    stopReason = StopReason.DAILY_LIMIT;
                    break;
                } catch (GeocodingTransientException e) {
                    counter.transientFailed++;
                    auditLogger.logGeocodingFailure(t.complexId(), refined.get().query(), e.getMessage(), true);
                    if (transientStreak.failed()) {
                        stopReason = StopReason.CONSECUTIVE_TRANSIENT_FAILURES;
                        break;
                    }
                    continue;
                }
                transientStreak.responded();

                if (coordinate != null) {
                    counter.found++;
                    if (PRECISION_APPROXIMATE.equals(coordinate.precision())) {
                        counter.foundByKeyword++;
                    }
                    if (retryFailed) {
                        redisState.clearNotFound(t.complexId());
                    }
                    pending.add(coordinate);
                    if (pending.size() >= properties.chunkSize()) {
                        counter.written += flush(pending);
                    }
                } else {
                    counter.notFound++;
                    redisState.markNotFound(t.complexId(), notFoundReason);
                    auditLogger.logGeocodingFailure(t.complexId(), refined.get().query(), notFoundReason, false);
                }
            }
        } catch (GeocodingConfigurationException e) {
            stopReason = StopReason.CONFIGURATION_ERROR;
            auditLogger.logBatchFailure("BAT-GEO-01", e);
        } catch (GeocodingRateLimitedException e) {
            stopReason = StopReason.RATE_LIMITED;
            auditLogger.logBatchFailure("BAT-GEO-01", e);
        } catch (GeocodingInterruptedException e) {
            stopReason = StopReason.INTERRUPTED;
            Thread.currentThread().interrupt();
        } finally {
            // 어떤 경로로 끝나든 이미 받은 좌표는 쓴다 — 다시 받으려면 쿼터를 또 써야 한다.
            counter.written += flush(pending);
        }

        RunResult runResult = new RunResult(targets.size(), counter.noJibun, counter.skipped, counter.found,
                counter.foundByKeyword, counter.written, counter.notFound, counter.transientFailed, counter.calls,
                stopReason);
        log.atInfo()
                .addKeyValue("programId", "BAT-GEO-01")
                .addKeyValue("retryFailed", retryFailed)
                .addKeyValue("targets", runResult.targets())
                .addKeyValue("found", runResult.found())
                .addKeyValue("foundByKeyword", runResult.foundByKeyword())
                .addKeyValue("written", runResult.written())
                .addKeyValue("notFound", runResult.notFound())
                .addKeyValue("noJibun", runResult.noJibun())
                .addKeyValue("skipped", runResult.skipped())
                .addKeyValue("transientFailed", runResult.transientFailed())
                .addKeyValue("calls", runResult.calls())
                .addKeyValue("stopReason", runResult.stopReason())
                .log("BAT-GEO-01 지오코딩 종료: 대상 {}건, 좌표 {}건(그중 키워드 {}건, 기록 {}건), 못 찾음 {}건, 지번 없음 {}건, "
                        + "건너뜀 {}건, 일시 실패 {}건, 호출 {}건, 종료 사유 {}", runResult.targets(), runResult.found(),
                        runResult.foundByKeyword(), runResult.written(), runResult.notFound(), runResult.noJibun(),
                        runResult.skipped(), runResult.transientFailed(), runResult.calls(), runResult.stopReason());
        return runResult;
    }

    /**
     * 주소 검색이 못 찾은 단지를 단지명 장소 검색으로 찾는다. 검색어는 {@link KeywordPlaceMatcher#queries} 순서다("{시군구} {단지명}",
     * 끝의 "아파트"를 뗀 것, 괄호를 지운 것). 앞 검색어에서 후보가 없거나 이름이 맞는 후보가 없으면 다음 검색어로 다시 찾고, 이름을
     * 포함하는 후보가 여럿이면 거기서 멈춘다(검색어를 넓혀도 여전히 여럿이거나 늘 뿐이다). 모든 검색어가 실패하면, 한 번이라도 이름
     * 불일치였으면 그 사유를, 아니면 후보 없음을 돌려준다.
     *
     * <p>판정은 검색어 하나의 결과 전체를 받은 뒤에 한다({@link #fetchAllKeywordPages}) — 첫 페이지만 보면 뒤 페이지의 맞는
     * 단지를 놓쳐 "후보 없음"을 30일 남기거나, 뒤 페이지의 같은 이름 후보를 못 보고 첫 페이지 후보를 잘못 채택할 수 있다. 결과
     * 전체를 받지 못하면(상한 페이지까지 받아도 끝이 아니면) {@code KEYWORD_INCOMPLETE}로 채택하지 않고 멈춘다.
     */
    private KeywordPlaceMatcher.Result searchByKeyword(Target t, Counter counter) {
        if (t.complexName() == null || t.complexName().isBlank() || t.sido() == null) {
            return new KeywordPlaceMatcher.NoMatch("KEYWORD_SKIPPED");
        }
        KeywordPlaceMatcher.ComplexIdentity identity = t.identity();
        boolean nameMismatchSeen = false;
        for (String query : KeywordPlaceMatcher.queries(identity)) {
            List<KakaoGeocodingClient.KeywordPlace> places = fetchAllKeywordPages(query, counter);
            if (places == null) {
                return new KeywordPlaceMatcher.NoMatch(REASON_INCOMPLETE);
            }
            KeywordPlaceMatcher.Result result = KeywordPlaceMatcher.match(identity, places);
            if (!(result instanceof KeywordPlaceMatcher.NoMatch noMatch) || REASON_AMBIGUOUS.equals(noMatch.reason())) {
                return result;
            }
            nameMismatchSeen |= REASON_NAME_MISMATCH.equals(noMatch.reason());
        }
        return new KeywordPlaceMatcher.NoMatch(nameMismatchSeen ? REASON_NAME_MISMATCH : REASON_NO_CANDIDATE);
    }

    /**
     * 검색어 하나의 키워드 결과를 마지막 페이지(is_end)까지 모은다. 페이지마다 {@link #callWithRetry}를 거쳐 일일 카운터·재시도·
     * 중단 규칙이 주소 검색과 같다(5xx 재시도는 그 페이지만 다시 보낸다). 카카오는 최대 45건을 15건씩 내주므로
     * {@link KakaoGeocodingClient#KEYWORD_MAX_PAGES}쪽 안에 끝나야 한다 — 그래도 끝나지 않으면 일부만 받은 결과라 null을
     * 돌려준다. 페이지 사이에 같은 문서가 다시 오면 한 번만 센다(두 번 세면 같은 단지가 여럿으로 보여 채택하지 못한다).
     */
    private List<KakaoGeocodingClient.KeywordPlace> fetchAllKeywordPages(String query, Counter counter) {
        Set<KakaoGeocodingClient.KeywordPlace> places = new LinkedHashSet<>();
        for (int page = 1; page <= KakaoGeocodingClient.KEYWORD_MAX_PAGES; page++) {
            int current = page;
            KakaoGeocodingClient.KeywordPage result = callWithRetry(() -> client.searchKeyword(query, current), counter);
            places.addAll(result.places());
            if (result.isEnd()) {
                return List.copyOf(places);
            }
        }
        log.atWarn()
                .addKeyValue("programId", "BAT-GEO-01")
                .addKeyValue("query", query)
                .log("BAT-GEO-01 키워드 검색 결과가 {}쪽 안에 끝나지 않아 판정하지 않는다: {}",
                        KakaoGeocodingClient.KEYWORD_MAX_PAGES, query);
        return null;
    }

    /**
     * 이미 저장한 APPROXIMATE 좌표를 현재 키워드 판정 규칙으로 다시 판정한다(러너 {@code --recheck-approximate}). 판정 규칙을 바꾼 뒤
     * 예전 규칙으로 채택한 좌표를 정리하는 경로다. 주소 검색은 이미 못 찾은 단지라 다시 하지 않는다.
     * <ul>
     *   <li>다시 채택되면 좌표가 같으면 그대로 두고, 다른 장소가 골라졌으면 그 좌표로 바꾼다(APPROXIMATE 유지).</li>
     *   <li>탈락하면 좌표를 NULL로 되돌리고 "못 찾음" 표시를 남긴다 — 이후 {@code --retry-failed}로 다시 돌릴 수 있다.</li>
     * </ul>
     * 호출 규칙(일일 카운터·429·401·403 중단·5xx 재시도·연속 일시 실패 시 중단)은 {@link #run}과 같다. 단지마다 바로 커밋한다
     * (건수가 적다).
     */
    public RecheckResult recheckApproximate(int maxComplexes) {
        List<ApproximateTarget> targets = complexRepository.findApproximateTargets().stream()
                .map(r -> new ApproximateTarget(new Target(((Number) r[0]).longValue(), (String) r[1], (String) r[2],
                        (String) r[3], (String) r[4], (String) r[5]), (BigDecimal) r[6], (BigDecimal) r[7]))
                .toList();
        Counter counter = new Counter();
        int kept = 0;
        int moved = 0;
        int dropped = 0;
        int checked = 0;
        TransientFailureStreak transientStreak = new TransientFailureStreak(properties.maxConsecutiveTransientFailures());
        StopReason stopReason = StopReason.COMPLETED;
        try {
            for (ApproximateTarget a : targets) {
                if (checked >= maxComplexes) {
                    stopReason = StopReason.MAX_COMPLEXES;
                    break;
                }
                KeywordPlaceMatcher.Result result;
                try {
                    result = searchByKeyword(a.target(), counter);
                } catch (DailyLimitReachedException e) {
                    stopReason = StopReason.DAILY_LIMIT;
                    break;
                } catch (GeocodingTransientException e) {
                    // 판정하지 못한 단지는 그대로 둔다(좌표 유지). 다음 recheck 실행이 다시 본다. 연달아 나오면 run()과 같은
                    // 기준으로 멈춘다 — 카카오 장애 중에 남은 단지를 모두 재시도하며 쿼터를 쓰지 않게 한다.
                    counter.transientFailed++;
                    auditLogger.logGeocodingFailure(a.target().complexId(), a.target().address(), e.getMessage(), true);
                    checked++;
                    if (transientStreak.failed()) {
                        stopReason = StopReason.CONSECUTIVE_TRANSIENT_FAILURES;
                        break;
                    }
                    continue;
                }
                transientStreak.responded();
                checked++;
                Long complexId = a.target().complexId();
                LocalDateTime now = LocalDateTime.now(clock);
                if (result instanceof KeywordPlaceMatcher.Matched matched) {
                    KakaoGeocodingClient.KeywordPlace place = matched.place();
                    if (place.latitude().compareTo(a.latitude()) == 0 && place.longitude().compareTo(a.longitude()) == 0) {
                        kept++;
                        continue;
                    }
                    transactionTemplate.execute(status -> complexRepository.updateApproximateCoordinates(
                            complexId, place.latitude(), place.longitude(), now));
                    moved++;
                    logKeywordMatch(a.target(), place);
                } else {
                    String reason = "RECHECK/" + ((KeywordPlaceMatcher.NoMatch) result).reason();
                    transactionTemplate.execute(status -> complexRepository.clearApproximateCoordinates(complexId, now));
                    redisState.markNotFound(complexId, reason);
                    auditLogger.logGeocodingFailure(complexId, a.target().address(), reason, false);
                    dropped++;
                }
                evictDetailCache(List.of(new Coordinate(complexId, null, null, null)));
            }
        } catch (GeocodingConfigurationException e) {
            stopReason = StopReason.CONFIGURATION_ERROR;
            auditLogger.logBatchFailure("BAT-GEO-01", e);
        } catch (GeocodingRateLimitedException e) {
            stopReason = StopReason.RATE_LIMITED;
            auditLogger.logBatchFailure("BAT-GEO-01", e);
        } catch (GeocodingInterruptedException e) {
            stopReason = StopReason.INTERRUPTED;
            Thread.currentThread().interrupt();
        }
        RecheckResult result = new RecheckResult(targets.size(), kept, moved, dropped, counter.transientFailed,
                counter.calls, stopReason);
        log.atInfo()
                .addKeyValue("programId", "BAT-GEO-01")
                .addKeyValue("recheckApproximate", true)
                .addKeyValue("targets", result.targets())
                .addKeyValue("kept", result.kept())
                .addKeyValue("moved", result.moved())
                .addKeyValue("dropped", result.dropped())
                .addKeyValue("transientFailed", result.transientFailed())
                .addKeyValue("calls", result.calls())
                .addKeyValue("stopReason", result.stopReason())
                .log("BAT-GEO-01 APPROXIMATE 재판정 종료: 대상 {}건, 유지 {}건, 좌표 변경 {}건, 탈락 {}건, 일시 실패 {}건, 호출 {}건, "
                        + "종료 사유 {}", result.targets(), result.kept(), result.moved(), result.dropped(),
                        result.transientFailed(), result.calls(), result.stopReason());
        return result;
    }

    private void logKeywordMatch(Target t, KakaoGeocodingClient.KeywordPlace place) {
        log.atInfo()
                .addKeyValue("auditEvent", "GEOCODING_KEYWORD_MATCH")
                .addKeyValue("complexId", t.complexId())
                .addKeyValue("complexName", t.complexName())
                .addKeyValue("address", t.address())
                .addKeyValue("placeName", place.placeName())
                .addKeyValue("placeAddress", place.addressName())
                .log("GEOCODING_KEYWORD_MATCH complexId={} place={}", t.complexId(), place.placeName());
    }

    /**
     * 호출 시도마다(재시도 포함, 응답이 오류여도) 오늘 카운터를 먼저 하나 늘린다. 5xx·네트워크 오류만 백오프 후 재시도하고,
     * 429·401·403은 클라이언트가 던진 예외 그대로 run()까지 올라가 그 실행을 멈춘다. 주소 검색과 키워드 검색이 같이 쓴다.
     */
    private <T> T callWithRetry(Supplier<T> call, Counter counter) {
        for (int attempt = 0; ; attempt++) {
            reserveCall();
            counter.calls++;
            pace();
            try {
                return call.get();
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
                        c.precision(), now))
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
                .map(r -> new Target(((Number) r[0]).longValue(), (String) r[1], (String) r[2], (String) r[3],
                        (String) r[4], (String) r[5]))
                .toList();
    }

    private static final class Counter {
        int noJibun;
        int skipped;
        int found;
        int foundByKeyword;
        int written;
        int notFound;
        int transientFailed;
        int calls;
    }

    /**
     * 재시도까지 실패한 단지가 연달아 나오는지 센다(카카오 장애 의심). {@link #run}(정기 런·러너 기본·{@code --retry-failed})과
     * {@link #recheckApproximate}가 같이 쓴다.
     * <ul>
     *   <li>{@link #failed()} — 한 단지의 처리가 {@link GeocodingTransientException}(5xx·네트워크 오류를 재시도까지 실패)으로
     *       끝났을 때 한 번 부른다. 단지 단위라 주소 검색이든 키워드 검색의 몇 번째 페이지든 그 단지는 1회로 센다.</li>
     *   <li>{@link #responded()} — 카카오가 정상 응답해 그 단지를 판정했을 때 부른다(좌표 채택·못 찾음·이름 불일치·후보 여럿·
     *       결과 미완 모두). 429·401·403과 일일 상한은 실행 자체를 멈추므로 여기서 세지 않는다.</li>
     * </ul>
     */
    private static final class TransientFailureStreak {
        private final int limit;
        private int count;

        TransientFailureStreak(int limit) {
            this.limit = limit;
        }

        /** @return 연속 실패가 상한에 닿아 실행을 멈춰야 하면 true */
        boolean failed() {
            return ++count >= limit;
        }

        void responded() {
            count = 0;
        }
    }

    private static final class DailyLimitReachedException extends RuntimeException {
    }

    private static final class GeocodingInterruptedException extends RuntimeException {
    }
}
