package com.jiseong.homesense.batch.matcher;

import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * complex.legal_dong_address·trade.jibun에서 지번 "(산)본번(-부번)"을 뽑는다. BAT-MAT-02(ComplexMasterMatcher)가
 * 쓰던 private 로직을 BAT-GEO-01(지오코딩 주소 정제)도 쓰게 되면서 그대로 옮겼다 — 정규식을 두 곳에 복제하면
 * 한쪽만 고쳐지는 드리프트가 생긴다(SigunguNormalizer를 뽑은 것과 같은 이유).
 *
 * <p>legal_dong_address는 실제로 "시도 시군구 동리 지번 단지명" 형태라 지번 뒤에 단지명이 붙는다. 그래서 문자열 끝($)이
 * 아니라 동리 바로 뒤에서 시작 앵커(lookingAt)로 지번만 읽는다. "73-"처럼 부번 없이 하이픈만 남은 경우는 본번만 읽힌다.
 */
public final class JibunExtractor {

    private static final Pattern JIBUN_LEADING_PATTERN = Pattern.compile("^(산)?\\s*([0-9]+(-[0-9]+)?)");
    private static final Pattern JIBUN_STANDALONE_PATTERN = Pattern.compile("^(산)?\\s*([0-9]+(-[0-9]+)?)$");

    /**
     * @param mountainLot 산번지 여부(일반 지번과 다른 필지다)
     * @param number      "본번" 또는 "본번-부번"(공백 없음)
     */
    public record Jibun(boolean mountainLot, String number) {

        /** BAT-MAT-02 비교 키 — "산123-4" / "123-4". */
        public String key() {
            return (mountainLot ? "산" : "") + number;
        }

        /** 주소 문자열에 붙이는 표기 — "산 123-4" / "123-4". */
        public String display() {
            return (mountainLot ? "산 " : "") + number;
        }
    }

    private JibunExtractor() {
    }

    /** API 원본의 단일 토큰("123-4", "산 45-6")을 전체 일치로 읽는다. */
    public static Optional<Jibun> parseStandalone(String raw) {
        if (raw == null) {
            return Optional.empty();
        }
        Matcher matcher = JIBUN_STANDALONE_PATTERN.matcher(raw.trim());
        return matcher.matches() ? Optional.of(toJibun(matcher)) : Optional.empty();
    }

    /** 문자열 맨 앞(앞 공백 무시)에서 지번을 읽는다. 뒤에 무엇이 붙어 있든 무시한다. */
    public static Optional<Jibun> parseLeading(String text) {
        if (text == null) {
            return Optional.empty();
        }
        Matcher matcher = JIBUN_LEADING_PATTERN.matcher(text.trim());
        return matcher.lookingAt() ? Optional.of(toJibun(matcher)) : Optional.empty();
    }

    /**
     * address에서 dongRi가 처음 나오는 위치 바로 뒤의 지번을 읽는다(BAT-MAT-02의 기존 동작 그대로 — indexOf 첫 위치).
     */
    public static Optional<Jibun> afterDongRi(String address, String dongRi) {
        if (address == null || dongRi == null) {
            return Optional.empty();
        }
        int idx = address.indexOf(dongRi);
        if (idx < 0) {
            return Optional.empty();
        }
        return parseLeading(address.substring(idx + dongRi.length()));
    }

    private static Jibun toJibun(Matcher matcher) {
        return new Jibun(matcher.group(1) != null, matcher.group(2).replaceAll("\\s+", ""));
    }
}
