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
import com.jiseong.homesense.complex.entity.Complex;
import com.jiseong.homesense.complex.repository.ComplexRepository;

import lombok.RequiredArgsConstructor;

/**
 * BAT-MAT-02. 시도/시군구/동리로 1차 필터링한 단지 후보를 지번 정규식·단지명 유사도로 매칭해
 * trade.complex_id/match_method/match_confidence를 결정한다(FR-2.5, 목표 성공률 98.9% 이상).
 */
@Component
@RequiredArgsConstructor
public class ComplexMasterMatcher {

    private static final Pattern JIBUN_PATTERN = Pattern.compile("^(산\\s*)?([0-9]+(-[0-9]+)?)");
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

    public MatchResult matchComplex(TradeDraft draft) {
        List<Complex> candidates = complexRepository.findBySidoAndSigunguAndDongRi(
                draft.sido(), draft.sigungu(), draft.dongRi());
        if (candidates.isEmpty()) {
            return MatchResult.unmatched();
        }

        Optional<String> draftJibun = normalizeJibun(draft.jibun());
        List<Complex> jibunMatches = draftJibun.isEmpty()
                ? List.of()
                : candidates.stream()
                        .filter(candidate -> draftJibun.equals(normalizeJibun(candidate.getLegalDongAddress())))
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
                .orElseGet(MatchResult::unmatched);
    }

    /**
     * "산" 접두 여부까지 일치해야 같은 지번으로 본다 — 산번지와 일반 지번은 다른 필지다.
     */
    private Optional<String> normalizeJibun(String raw) {
        if (raw == null) {
            return Optional.empty();
        }
        Matcher matcher = JIBUN_PATTERN.matcher(raw.trim());
        if (!matcher.lookingAt()) {
            return Optional.empty();
        }
        boolean isMountainLot = matcher.group(1) != null;
        String number = matcher.group(2).replaceAll("\\s+", "");
        return Optional.of((isMountainLot ? "산" : "") + number);
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
