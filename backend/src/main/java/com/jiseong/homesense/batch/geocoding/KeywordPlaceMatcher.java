package com.jiseong.homesense.batch.geocoding;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;

import com.jiseong.homesense.batch.geocoding.KakaoGeocodingClient.KeywordPlace;
import com.jiseong.homesense.batch.matcher.SigunguNormalizer;

/**
 * BAT-GEO-01 키워드 보완 경로의 후보 판정. 주소 검색이 못 찾은 단지(주로 재건축 단지 — K-apt가 사업 전 지번을 갖고 있다)를
 * 단지명 장소 검색으로 찾을 때, <b>다른 단지를 잘못 집지 않는 것</b>을 우선한다. 조건을 모두 통과한 후보가 정확히 하나일 때만
 * 채택하고, 0개이거나 여러 개면 못 찾음으로 남긴다.
 *
 * <ul>
 *   <li>카테고리: category_name을 " > "로 나눈 단계 중 하나가 "주거시설" 또는 "아파트"와 정확히 같아야 한다. 부분 문자열로
 *       비교하면 "가정,생활 > 상가,아케이드 > 아파트상가"도 통과해 같은 단지의 상가가 두 번째 후보가 된다(실측: 더비치푸르지오써밋).</li>
 *   <li>지역: address_name(지번 주소)이 단지의 시도·시군구로 시작하고, 그 뒤 토큰 중 하나가 동·리 이름과 같아야 한다. 비교할 때
 *       공백을 지운다. 카카오는 시도를 줄여 쓰고("서울", "경기", "충남" — 2026-10-12 시도별 실측), 구가 있는 시를 두 토큰으로 쓴다
 *       ("화성시 동탄구") — 뒤쪽은 {@link SigunguNormalizer}로 K-apt 표기("화성동탄구")에 맞춘다.</li>
 * </ul>
 */
public final class KeywordPlaceMatcher {

    private static final String APARTMENT_SUFFIX = "아파트";
    private static final List<String> RESIDENTIAL_SEGMENTS = List.of("주거시설", "아파트");

    /** K-apt 시도 → 카카오 address_name 첫 토큰(2026-10-12 시도별 실측). 목록에 없는 시도는 카카오도 원래 이름을 쓴다. */
    private static final Map<String, String> KAKAO_SIDO = Map.ofEntries(
            Map.entry("서울특별시", "서울"), Map.entry("부산광역시", "부산"), Map.entry("대구광역시", "대구"),
            Map.entry("인천광역시", "인천"), Map.entry("광주광역시", "광주"), Map.entry("대전광역시", "대전"),
            Map.entry("울산광역시", "울산"), Map.entry("경기도", "경기"), Map.entry("충청북도", "충북"),
            Map.entry("충청남도", "충남"), Map.entry("전라남도", "전남"), Map.entry("경상북도", "경북"),
            Map.entry("경상남도", "경남"));

    /** 단지 쪽 비교 기준(complex 컬럼 그대로). */
    public record ComplexIdentity(String sido, String sigungu, String dongRi, String complexName) {
    }

    public sealed interface Result permits Matched, NoMatch {
    }

    public record Matched(KeywordPlace place) implements Result {
    }

    /** @param reason "KEYWORD_NO_CANDIDATE" 또는 "KEYWORD_AMBIGUOUS" */
    public record NoMatch(String reason) implements Result {
    }

    private KeywordPlaceMatcher() {
    }

    /**
     * 검색어: "{시군구} {단지명}"(세종처럼 시군구가 없으면 시도). 단지명이 "아파트"로 끝나면 그것을 뗀 검색어를 두 번째로 둔다 —
     * 첫 검색어로 후보를 못 찾으면 두 번째로 다시 찾는다.
     */
    public static List<String> queries(ComplexIdentity complex) {
        String region = complex.sigungu() != null && !complex.sigungu().isBlank() ? complex.sigungu() : complex.sido();
        String name = complex.complexName().trim();
        List<String> queries = new ArrayList<>();
        queries.add(region + " " + name);
        if (name.endsWith(APARTMENT_SUFFIX)) {
            String stripped = name.substring(0, name.length() - APARTMENT_SUFFIX.length()).trim();
            if (!stripped.isEmpty()) {
                queries.add(region + " " + stripped);
            }
        }
        return queries;
    }

    public static Result match(ComplexIdentity complex, List<KeywordPlace> places) {
        List<KeywordPlace> candidates = places.stream()
                .filter(place -> isResidential(place.categoryName()))
                .filter(place -> isInRegion(complex, place.addressName()))
                .toList();
        if (candidates.size() == 1) {
            return new Matched(candidates.get(0));
        }
        return new NoMatch(candidates.isEmpty() ? "KEYWORD_NO_CANDIDATE" : "KEYWORD_AMBIGUOUS");
    }

    static boolean isResidential(String categoryName) {
        if (categoryName == null) {
            return false;
        }
        return Arrays.stream(categoryName.split(">")).map(String::trim).anyMatch(RESIDENTIAL_SEGMENTS::contains);
    }

    static boolean isInRegion(ComplexIdentity complex, String addressName) {
        if (addressName == null || complex.sido() == null || complex.dongRi() == null) {
            return false;
        }
        List<String> tokens = Arrays.stream(addressName.trim().split("\\s+")).filter(t -> !t.isEmpty()).toList();
        if (tokens.size() < 2) {
            return false;
        }
        String sido = noSpace(complex.sido());
        if (!tokens.get(0).equals(sido) && !tokens.get(0).equals(KAKAO_SIDO.getOrDefault(sido, sido))) {
            return false;
        }
        int dongFrom = 1;
        if (complex.sigungu() != null && !complex.sigungu().isBlank()) {
            String sigungu = noSpace(complex.sigungu());
            if (noSpace(tokens.get(1)).equals(sigungu)) {
                dongFrom = 2;
            } else if (tokens.size() >= 3
                    && noSpace(SigunguNormalizer.normalize(tokens.get(1) + " " + tokens.get(2))).equals(sigungu)) {
                dongFrom = 3;
            } else {
                return false;
            }
        }
        String dongRi = noSpace(complex.dongRi());
        return tokens.subList(dongFrom, tokens.size()).stream().anyMatch(token -> token.equals(dongRi));
    }

    private static String noSpace(String value) {
        return value.replaceAll("\\s+", "");
    }
}
