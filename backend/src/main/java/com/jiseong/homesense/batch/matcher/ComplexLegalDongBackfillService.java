package com.jiseong.homesense.batch.matcher;

import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.TreeMap;
import java.util.function.Function;

import org.springframework.cache.Cache;
import org.springframework.cache.CacheManager;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;

import com.jiseong.homesense.common.cache.CacheNames;
import com.jiseong.homesense.complex.repository.ComplexRepository;
import com.jiseong.homesense.region.entity.LegalDistrictCode;
import com.jiseong.homesense.region.repository.LegalDistrictCodeRepository;
import com.jiseong.homesense.trade.repository.TradeRepository;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * complex.legal_dong_cd 백필. 단지 기본정보는 저장소 밖에서 1회 수동 적재돼 이 컬럼을 채운 경로가
 * 한 번도 없었다(21,680건 전부 NULL) — 이 서비스가 그 공백을 메우고, 단지를 새로 적재한 뒤에도 같은
 * 러너를 다시 돌리면 NULL인 행만 채운다(CLAUDE.md "단지 검색 지역코드·키워드" 절 참고).
 *
 * <p>{@link #dryRun}은 DB에 쓰지 않고 방식별 채움률과 거래 대조 일치율만 로그로 남긴다.
 * {@link #apply}는 {@code legal_dong_cd IS NULL}인 행만 갱신하므로 재실행해도 안전하다.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ComplexLegalDongBackfillService {

    public enum Strategy { NAME, PREFIX, COMBINED }

    private static final ZoneId KST = ZoneId.of("Asia/Seoul");
    /**
     * 백필 후 비우는 캐시. complexDetailV2는 matchPending이 legal_dong_cd로 계산되고, popularComplexesV3와
     * regionAutocomplete는 지역 정보를 담는 DTO라 함께 비운다(regionAutocomplete는 이 백필로 값이 바뀌지
     * 않지만 "지역 정보를 담는 캐시 전부"라는 운영 절차를 단순하게 유지하려고 포함했다).
     */
    static final List<String> CACHES_TO_CLEAR =
            List.of(CacheNames.COMPLEX_DETAIL, CacheNames.POPULAR_COMPLEXES, CacheNames.REGION_AUTOCOMPLETE);
    private static final int WRITE_CHUNK = 500;
    private static final int SAMPLE_LIMIT = 15;

    private final ComplexRepository complexRepository;
    private final LegalDistrictCodeRepository legalDistrictCodeRepository;
    private final TradeRepository tradeRepository;
    private final TransactionTemplate transactionTemplate;
    private final CacheManager cacheManager;

    private record Target(Long complexId, String sido, String sigungu, String dongRi, String address) {
    }

    public void dryRun() {
        ComplexLegalDongResolver resolver = loadResolver();
        List<Target> targets = loadTargets();
        Map<Long, String> tradeMode = loadTradeModes();
        log.info("[backfill dry-run] 대상 단지 {}건, 거래가 연결된 대상 단지 {}건", targets.size(),
                targets.stream().filter(t -> tradeMode.containsKey(t.complexId())).count());

        for (Strategy strategy : Strategy.values()) {
            report(strategy, targets, tradeMode, t -> resolve(resolver, strategy, t));
        }

        List<String> disagreements = new ArrayList<>();
        for (Target t : targets) {
            Optional<LegalDistrictCode> a = resolve(resolver, Strategy.NAME, t);
            Optional<LegalDistrictCode> b = resolve(resolver, Strategy.PREFIX, t);
            if (a.isPresent() && b.isPresent() && !a.get().getLegalDongCd().equals(b.get().getLegalDongCd())) {
                disagreements.add(t.complexId() + " addr=" + t.address() + " name=" + a.get().getLegalDongCd()
                        + " prefix=" + b.get().getLegalDongCd() + " trade=" + tradeMode.get(t.complexId()));
            }
        }
        log.info("[backfill dry-run] NAME·PREFIX 둘 다 매칭됐는데 결과가 다른 단지 {}건", disagreements.size());
        disagreements.stream().limit(SAMPLE_LIMIT).forEach(line -> log.info("  불일치(NAME≠PREFIX) {}", line));
    }

    public void apply(Strategy strategy) {
        ComplexLegalDongResolver resolver = loadResolver();
        List<Target> targets = loadTargets();
        Map<Long, String> resolved = new LinkedHashMap<>();
        for (Target t : targets) {
            resolve(resolver, strategy, t).ifPresent(code -> resolved.put(t.complexId(), code.getLegalDongCd()));
        }

        LocalDateTime now = LocalDateTime.now(KST);
        List<Map.Entry<Long, String>> entries = new ArrayList<>(resolved.entrySet());
        int updated = 0;
        for (int from = 0; from < entries.size(); from += WRITE_CHUNK) {
            List<Map.Entry<Long, String>> chunk = entries.subList(from, Math.min(from + WRITE_CHUNK, entries.size()));
            Integer chunkUpdated = transactionTemplate.execute(status -> chunk.stream()
                    .mapToInt(e -> complexRepository.fillLegalDongCdIfNull(e.getKey(), e.getValue(), now))
                    .sum());
            updated += chunkUpdated == null ? 0 : chunkUpdated;
        }
        log.info("[backfill apply] strategy={} 대상 {}건, 매칭 {}건, 실제 갱신 {}건, 미매칭 {}건", strategy,
                targets.size(), resolved.size(), updated, targets.size() - resolved.size());

        clearCaches();
    }

    /**
     * 캐시 비우기 실패는 이미 커밋된 백필을 무효로 만들 이유가 없어 흡수한다 — 최악의 경우 TTL(24h) 동안
     * stale. 대신 실패한 캐시 이름을 WARN으로 남겨 운영자가 runbook의 수동 비우기 단계를 밟게 한다.
     */
    private void clearCaches() {
        for (String name : CACHES_TO_CLEAR) {
            try {
                Cache cache = cacheManager.getCache(name);
                if (cache != null) {
                    cache.clear();
                    log.info("[backfill apply] 캐시 비움: {}", name);
                }
            } catch (RuntimeException e) {
                log.warn("[backfill apply] 캐시 비우기 실패: {} — runbook의 수동 비우기 단계를 실행하라", name, e);
            }
        }
    }

    private Optional<LegalDistrictCode> resolve(ComplexLegalDongResolver resolver, Strategy strategy, Target t) {
        return switch (strategy) {
            case NAME -> resolver.byName(t.sido(), t.sigungu(), t.dongRi(), t.address());
            case PREFIX -> resolver.byAddressPrefix(t.address());
            case COMBINED -> resolver.resolve(t.sido(), t.sigungu(), t.dongRi(), t.address());
        };
    }

    private void report(Strategy strategy, List<Target> targets, Map<Long, String> tradeMode,
            Function<Target, Optional<LegalDistrictCode>> fn) {
        int matched = 0;
        int compared = 0;
        int agreed = 0;
        Map<String, Integer> unmatchedByRegion = new TreeMap<>();
        Map<String, List<String>> unmatchedSamples = new HashMap<>();
        List<String> mismatches = new ArrayList<>();

        for (Target t : targets) {
            Optional<LegalDistrictCode> code = fn.apply(t);
            if (code.isEmpty()) {
                String region = t.sido() + " " + Objects.toString(t.sigungu(), "-");
                unmatchedByRegion.merge(region, 1, Integer::sum);
                unmatchedSamples.computeIfAbsent(region, k -> new ArrayList<>())
                        .add(t.complexId() + " dongRi=" + t.dongRi() + " addr=" + t.address());
                continue;
            }
            matched++;
            String expected = tradeMode.get(t.complexId());
            if (expected != null) {
                compared++;
                if (expected.equals(code.get().getLegalDongCd())) {
                    agreed++;
                } else {
                    mismatches.add(t.complexId() + " addr=" + t.address() + " backfill="
                            + code.get().getLegalDongCd() + " tradeMode=" + expected);
                }
            }
        }

        log.info("[backfill dry-run] strategy={} 채움 {}/{} ({}%), 거래 대조 일치 {}/{} ({}%)", strategy, matched,
                targets.size(), pct(matched, targets.size()), agreed, compared, pct(agreed, compared));
        if (strategy == Strategy.COMBINED) {
            unmatchedByRegion.entrySet().stream()
                    .sorted(Map.Entry.<String, Integer>comparingByValue().reversed())
                    .forEach(e -> log.info("  미매칭 {}: {}건 예) {}", e.getKey(), e.getValue(),
                            unmatchedSamples.get(e.getKey()).stream().limit(2).toList()));
            mismatches.stream().limit(SAMPLE_LIMIT).forEach(line -> log.info("  거래 대조 불일치 {}", line));
            log.info("  거래 대조 불일치 총 {}건", mismatches.size());
        }
    }

    private ComplexLegalDongResolver loadResolver() {
        List<LegalDistrictCode> codes = legalDistrictCodeRepository.findAll().stream()
                .filter(LegalDistrictCode::isActive)
                .toList();
        return ComplexLegalDongResolver.of(codes);
    }

    private List<Target> loadTargets() {
        return complexRepository.findAddressFieldsWithoutLegalDongCd().stream()
                .map(r -> new Target(((Number) r[0]).longValue(), (String) r[1], (String) r[2], (String) r[3],
                        (String) r[4]))
                .toList();
    }

    /** 단지별 거래 legal_dong_cd 최빈값. 동률이면 코드 문자열이 작은 쪽(결정적). */
    private Map<Long, String> loadTradeModes() {
        Map<Long, String> mode = new HashMap<>();
        Map<Long, Long> best = new HashMap<>();
        for (Object[] row : tradeRepository.countLegalDongCdByComplex()) {
            Long complexId = ((Number) row[0]).longValue();
            String code = (String) row[1];
            long count = ((Number) row[2]).longValue();
            Long current = best.get(complexId);
            if (current == null || count > current || (count == current && code.compareTo(mode.get(complexId)) < 0)) {
                best.put(complexId, count);
                mode.put(complexId, code);
            }
        }
        return mode;
    }

    private static String pct(int numerator, int denominator) {
        return denominator == 0 ? "-" : String.format("%.2f", 100.0 * numerator / denominator);
    }
}
