package com.jiseong.homesense.batch.matcher;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.springframework.stereotype.Component;

import com.jiseong.homesense.batch.parser.dto.TradeDraft;
import com.jiseong.homesense.common.logging.AuditLogger;
import com.jiseong.homesense.complex.entity.Complex;
import com.jiseong.homesense.complex.repository.ComplexRepository;
import com.jiseong.homesense.region.entity.LegalDistrictCode;

import lombok.RequiredArgsConstructor;

/**
 * BAT-MAT-02. 시도/시군구/동리로 1차 필터링한 단지 후보를 지번 정규식·단지명 유사도로 매칭해
 * trade.complex_id/match_method/match_confidence를 결정한다(FR-2.5, 목표 성공률 98.9% 이상).
 */
@Component
@RequiredArgsConstructor
public class ComplexMasterMatcher {

    /**
     * legal_dong_address는 실제로는 "시도 시군구 동리 지번 단지명" 형태다 — 지번 뒤에 단지명이 그대로
     * 이어 붙는다(실 DB 데이터로 확인, 예: "서울특별시 종로구 숭인동 766 종로청계힐스테이트"). 예전에는
     * 이 컬럼이 지번으로 끝난다고 가정해 문자열 끝($)에 anchor한 정규식을 썼는데, 그 가정이 틀려
     * match_method=EXACT가 전국 0건이 되는 원인이었다(dongRi 뒤에서 지번을 추출하는
     * {@link #extractJibunFromAddress(Complex)}로 대체). draft.jibun()은 단일 토큰이라
     * {@link #normalizeStandaloneJibun(String)}으로 전체 일치를 그대로 검사한다.
     */
    private static final Pattern JIBUN_LEADING_PATTERN = Pattern.compile("^(산)?\\s*([0-9]+(-[0-9]+)?)");
    private static final Pattern JIBUN_STANDALONE_PATTERN = Pattern.compile("^(산)?\\s*([0-9]+(-[0-9]+)?)$");
    private static final int NGRAM_SIZE = 3;

    private static final BigDecimal EXACT_CONFIDENCE = new BigDecimal("1.000");
    /**
     * 지번은 일치하지만 단지명이 다른 경우(개명 등) — 지번 자체는 신뢰할 수 있는 근거이므로
     * match_method는 EXACT를 유지하되 match_confidence만 낮춘다.
     */
    private static final BigDecimal EXACT_RENAMED_CONFIDENCE = new BigDecimal("0.800");

    private static final double SIMILAR_THRESHOLD = 0.500;
    private static final double SIMILAR_MIN_CONFIDENCE = 0.600;
    private static final double SIMILAR_MAX_CONFIDENCE = 0.850;

    private final ComplexRepository complexRepository;
    private final AuditLogger auditLogger;

    /**
     * legalDistrictCode는 BAT-MAT-01(LegalDistrictMatcher)이 이미 sgg_cd·umd_nm으로 해석한 결과다.
     * draft 자체에는 원본 API의 sgg_cd·umd_nm만 있고 시도/시군구/동리 명칭이 없어, 지역 1차 필터링에는
     * 반드시 이 값을 받아야 한다. BAT-MAT-01이 매칭에 실패해 null이면 후보를 좁힐 지역 정보가 없으므로
     * 곧바로 매칭 실패로 처리한다.
     */
    public MatchResult matchComplex(TradeDraft draft, LegalDistrictCode legalDistrictCode) {
        if (legalDistrictCode == null) {
            return unmatched(draft, "법정동코드 매핑 실패로 후보 지역을 특정할 수 없음");
        }

        List<Complex> candidates = complexRepository.findBySidoAndSigunguAndDongRi(
                legalDistrictCode.getSidoName(), normalizeSigungu(legalDistrictCode.getSigunguName()),
                legalDistrictCode.getEupmyeondongName());
        if (candidates.isEmpty()) {
            return unmatched(draft, "동일 시도/시군구/동리에 단지 후보 없음");
        }

        Optional<String> draftJibun = normalizeStandaloneJibun(draft.jibun());
        List<Complex> jibunMatches = draftJibun.isEmpty()
                ? List.of()
                : candidates.stream()
                        .filter(candidate -> draftJibun.equals(extractJibunFromAddress(candidate)))
                        .toList();

        if (!jibunMatches.isEmpty()) {
            return jibunMatches.stream()
                    .filter(candidate -> isNameExactMatch(draft.buildingName(), candidate.getComplexName()))
                    .findFirst()
                    .map(candidate -> MatchResult.exact(candidate.getComplexId(), EXACT_CONFIDENCE))
                    .orElseGet(() -> MatchResult.exact(jibunMatches.get(0).getComplexId(), EXACT_RENAMED_CONFIDENCE));
        }

        return findBestSimilarCandidate(draft.buildingName(), candidates)
                .map(best -> MatchResult.similar(best.complex().getComplexId(), similarConfidence(best.similarity())))
                .orElseGet(() -> unmatched(draft, "지번 불일치 및 단지명 유사도 임계치 미달"));
    }

    private MatchResult unmatched(TradeDraft draft, String reason) {
        auditLogger.logMatchingFailure(draft, reason);
        return MatchResult.unmatched();
    }

    /**
     * "시+구" 구조 도시(수원/성남/청주 등)에서 xlsx 유래 complex.sigungu(예: "수원장안구", 공백 없음·
     * "시" 생략)와 CSV 유래 legal_district_code.sigungu_name(예: "수원시 장안구", 공백 있음·"시" 유지)의
     * 표기가 달라 1차 필터링 후보가 항상 0건이 되던 문제를 해결한다(BAT-MAT-02 1차 필터링, 실 DB로
     * 확인). 공백을 제거한 뒤, 문자열 끝이 아닌 위치의 "시"만 제거한다 — "목포시"처럼 "시"가 마지막
     * 글자면 보존해 단일 시/군 표기는 그대로 둔다. legal_district_code 쪽(CSV 유래) 값에만 적용하고
     * complex.sigungu(xlsx 원본)는 절대 건드리지 않는다 — 두 원천 모두 정부 원본 표기를 그대로 DB에
     * 보존해야 하므로 정규화는 비교 시점에만 수행한다.
     */
    private String normalizeSigungu(String raw) {
        if (raw == null) {
            return null;
        }
        String noSpace = raw.replaceAll("\\s+", "");
        return noSpace.replaceAll("시(?=.)", "");
    }

    /**
     * draft.jibun()은 API 원본의 단일 토큰이라("123-4", "산 45-6" 등) 전체 일치로 검사한다.
     * "산" 접두 여부까지 일치해야 같은 지번으로 본다 — 산번지와 일반 지번은 다른 필지다.
     */
    private Optional<String> normalizeStandaloneJibun(String raw) {
        if (raw == null) {
            return Optional.empty();
        }
        Matcher matcher = JIBUN_STANDALONE_PATTERN.matcher(raw.trim());
        if (!matcher.matches()) {
            return Optional.empty();
        }
        return Optional.of(jibunKey(matcher));
    }

    /**
     * legal_dong_address는 "...동리 지번 단지명" 형태로 지번 뒤에 단지명이 그대로 이어 붙는다 —
     * candidate.dongRi로 동리 텍스트가 끝나는 위치를 먼저 찾고, 그 바로 뒤에서 지번 토큰만 시작
     * 앵커(lookingAt)로 추출한다. 단지명이 숫자로 시작하든 아니든 뒤에 남는 텍스트는 매치 대상이
     * 아니므로 영향받지 않는다.
     */
    private Optional<String> extractJibunFromAddress(Complex candidate) {
        String address = candidate.getLegalDongAddress();
        String dongRi = candidate.getDongRi();
        if (address == null || dongRi == null) {
            return Optional.empty();
        }
        int idx = address.indexOf(dongRi);
        if (idx < 0) {
            return Optional.empty();
        }
        String remainder = address.substring(idx + dongRi.length()).trim();
        Matcher matcher = JIBUN_LEADING_PATTERN.matcher(remainder);
        if (!matcher.lookingAt()) {
            return Optional.empty();
        }
        return Optional.of(jibunKey(matcher));
    }

    private String jibunKey(Matcher matcher) {
        boolean isMountainLot = matcher.group(1) != null;
        String number = matcher.group(2).replaceAll("\\s+", "");
        return (isMountainLot ? "산" : "") + number;
    }

    private boolean isNameExactMatch(String draftName, String complexName) {
        if (draftName == null || complexName == null) {
            return false;
        }
        return draftName.trim().equals(complexName.trim());
    }

    private Optional<ScoredCandidate> findBestSimilarCandidate(String draftName, List<Complex> candidates) {
        ScoredCandidate best = null;
        for (Complex candidate : candidates) {
            double similarity = nameSimilarity(draftName, candidate.getComplexName());
            if (similarity >= SIMILAR_THRESHOLD && (best == null || similarity > best.similarity())) {
                best = new ScoredCandidate(candidate, similarity);
            }
        }
        return Optional.ofNullable(best);
    }

    private BigDecimal similarConfidence(double similarity) {
        double clamped = Math.min(similarity, 1.0);
        double confidence = SIMILAR_MIN_CONFIDENCE
                + (clamped - SIMILAR_THRESHOLD) / (1.0 - SIMILAR_THRESHOLD)
                        * (SIMILAR_MAX_CONFIDENCE - SIMILAR_MIN_CONFIDENCE);
        return BigDecimal.valueOf(confidence).setScale(3, RoundingMode.HALF_UP);
    }

    /**
     * 트라이그램(3-gram) 다이스 계수 기반 텍스트 유사도. 이름 길이가 3자 미만이면 문자열 전체를 하나의 토큰으로 취급한다.
     */
    private double nameSimilarity(String a, String b) {
        String normalizedA = normalizeForSimilarity(a);
        String normalizedB = normalizeForSimilarity(b);
        if (normalizedA.isEmpty() || normalizedB.isEmpty()) {
            return 0.0;
        }
        if (normalizedA.equals(normalizedB)) {
            return 1.0;
        }

        List<String> gramsA = ngrams(normalizedA);
        List<String> gramsBOriginal = ngrams(normalizedB);
        List<String> gramsB = new ArrayList<>(gramsBOriginal);
        int matches = 0;
        for (String gram : gramsA) {
            if (gramsB.remove(gram)) {
                matches++;
            }
        }
        return (2.0 * matches) / (gramsA.size() + gramsBOriginal.size());
    }

    private String normalizeForSimilarity(String s) {
        return s == null ? "" : s.replaceAll("\\s+", "");
    }

    private List<String> ngrams(String s) {
        if (s.length() < NGRAM_SIZE) {
            return List.of(s);
        }
        List<String> grams = new ArrayList<>();
        for (int i = 0; i <= s.length() - NGRAM_SIZE; i++) {
            grams.add(s.substring(i, i + NGRAM_SIZE));
        }
        return grams;
    }

    private record ScoredCandidate(Complex complex, double similarity) {
    }
}
