package com.jiseong.homesense.batch.geocoding;

import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.regex.Pattern;

import com.jiseong.homesense.batch.geocoding.KakaoGeocodingClient.KeywordPlace;
import com.jiseong.homesense.batch.matcher.SigunguNormalizer;

/**
 * BAT-GEO-01 키워드 보완 경로의 후보 판정. 주소 검색이 못 찾은 단지(주로 재건축 단지 — K-apt가 사업 전 지번을 갖고 있다)를
 * 단지명 장소 검색으로 찾을 때, <b>다른 단지를 잘못 집지 않는 것</b>을 우선한다. 아래 카테고리·지역 조건을 통과한 후보에
 * 이름 확인을 더해 고른다.
 *
 * <ul>
 *   <li>카테고리: category_name을 " > "로 나눈 단계 중 하나가 "주거시설" 또는 "아파트"와 정확히 같아야 한다. 부분 문자열로
 *       비교하면 "가정,생활 > 상가,아케이드 > 아파트상가"도 통과해 같은 단지의 상가가 두 번째 후보가 된다(실측: 더비치푸르지오써밋).</li>
 *   <li>지역: address_name(지번 주소)이 단지의 시도·시군구로 시작하고, 그 뒤 토큰 중 하나가 동·리 이름과 같아야 한다. 비교할 때
 *       공백을 지운다. 카카오는 시도를 줄여 쓰고("서울", "경기", "충남" — 2026-10-12 시도별 실측), 구가 있는 시를 두 토큰으로 쓴다
 *       ("화성시 동탄구") — 뒤쪽은 {@link SigunguNormalizer}로 K-apt 표기("화성동탄구")에 맞춘다.</li>
 *   <li>이름(2026-10-12 추가): 정규화한({@link #normalizeName}) 단지명과 장소명이 정확히 같은 후보가 하나면 채택한다(다른 후보가
 *       있어도). 아니면 한쪽이 다른 쪽을 포함하는 후보가 정확히 하나일 때만 채택한다. 둘 다 아니면 못 찾음이다 — 조건을 통과한
 *       후보가 하나뿐이어도 이름이 전혀 다르면 채택하지 않는다(실측: 동탄파크자이 → "동탄역자이아파트").</li>
 * </ul>
 */
public final class KeywordPlaceMatcher {

    private static final String APARTMENT_SUFFIX = "아파트";
    private static final List<String> RESIDENTIAL_SEGMENTS = List.of("주거시설", "아파트");
    private static final Pattern PARENTHESIZED = Pattern.compile("[(（\\[][^)）\\]]*[)）\\]]");
    private static final Pattern NOT_LETTER_OR_DIGIT = Pattern.compile("[^\\p{L}\\p{N}]");

    /**
     * 이름 표기 차이 — 2026-10-12 키워드 보완 결과에서 실제로 본 것만 둔다("감전엘에이치" ↔ "감전LH아파트", "테헤란 IPARK" ↔
     * "테헤란아이파크아파트", "영통 SK VIEW" ↔ "영통SK뷰아파트"). 양쪽 이름에 똑같이 적용하므로 방향은 상관없다.
     */
    private static final Map<String, String> SPELLING = new LinkedHashMap<>(Map.of(
            "엘에이치", "lh", "아이파크", "ipark", "뷰", "view"));

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

    /**
     * @param reason "KEYWORD_NO_CANDIDATE"(카테고리·지역 조건을 통과한 후보 없음), "KEYWORD_NAME_MISMATCH"(통과한 후보는 있지만
     *               이름이 맞는 후보가 없음), "KEYWORD_AMBIGUOUS"(이름을 포함하는 후보가 여럿)
     */
    public record NoMatch(String reason) implements Result {
    }

    private KeywordPlaceMatcher() {
    }

    /**
     * 검색어를 순서대로 돌려준다(중복 제거). "{시군구} {단지명}"(세종처럼 시군구가 없으면 시도), 단지명이 "아파트"로 끝나면 그것을 뗀
     * 것, 단지명에 괄호가 있으면 괄호와 그 안을 지운 것("왕십리텐즈힐1단지(임대)" → "왕십리텐즈힐1단지"). 앞 검색어로 고르지 못하면
     * 다음 검색어로 다시 찾는다. 괄호 제거 검색어는 띄어쓰기를 남긴다 — 소문자화·표기 맞춤은 비교용이라 검색어에는 쓰지 않는다.
     */
    public static List<String> queries(ComplexIdentity complex) {
        String region = complex.sigungu() != null && !complex.sigungu().isBlank() ? complex.sigungu() : complex.sido();
        String name = complex.complexName().trim();
        Set<String> names = new LinkedHashSet<>();
        names.add(name);
        names.add(stripApartmentSuffix(name));
        names.add(PARENTHESIZED.matcher(name).replaceAll(" ").replaceAll("\\s+", " ").trim());
        return names.stream().filter(n -> !n.isEmpty()).map(n -> region + " " + n).toList();
    }

    public static Result match(ComplexIdentity complex, List<KeywordPlace> places) {
        List<KeywordPlace> candidates = places.stream()
                .filter(place -> isResidential(place.categoryName()))
                .filter(place -> isInRegion(complex, place.addressName()))
                .toList();
        if (candidates.isEmpty()) {
            return new NoMatch("KEYWORD_NO_CANDIDATE");
        }
        String complexName = normalizeName(complex.complexName());
        if (complexName.isEmpty()) {
            return new NoMatch("KEYWORD_NAME_MISMATCH");
        }
        List<KeywordPlace> exact = candidates.stream()
                .filter(place -> normalizeName(place.placeName()).equals(complexName))
                .toList();
        if (exact.size() == 1) {
            return new Matched(exact.get(0));
        }
        List<KeywordPlace> containing = candidates.stream()
                .filter(place -> nameContains(complexName, normalizeName(place.placeName())))
                .toList();
        if (containing.size() == 1) {
            return new Matched(containing.get(0));
        }
        return new NoMatch(containing.isEmpty() ? "KEYWORD_NAME_MISMATCH" : "KEYWORD_AMBIGUOUS");
    }

    /**
     * 이름 비교용 정규화: 괄호와 그 안 내용을 지우고, 소문자로 바꾸고, 글자·숫자가 아닌 것(공백·특수문자)을 지운 뒤, 끝의 "아파트"를
     * 떼고, 실측한 표기 차이({@link #SPELLING})를 맞춘다. 예: "고척아이파크아파트(MD)" → "고척ipark", "감전엘에이치" → "감전lh".
     */
    public static String normalizeName(String name) {
        if (name == null) {
            return "";
        }
        String normalized = PARENTHESIZED.matcher(name).replaceAll("").toLowerCase(Locale.ROOT);
        normalized = NOT_LETTER_OR_DIGIT.matcher(normalized).replaceAll("");
        normalized = stripApartmentSuffix(normalized);
        for (Map.Entry<String, String> e : SPELLING.entrySet()) {
            normalized = normalized.replace(e.getKey(), e.getValue());
        }
        return normalized;
    }

    private static boolean nameContains(String a, String b) {
        return !a.isEmpty() && !b.isEmpty() && (a.contains(b) || b.contains(a));
    }

    private static String stripApartmentSuffix(String name) {
        String trimmed = name.trim();
        return trimmed.endsWith(APARTMENT_SUFFIX)
                ? trimmed.substring(0, trimmed.length() - APARTMENT_SUFFIX.length()).trim()
                : trimmed;
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
