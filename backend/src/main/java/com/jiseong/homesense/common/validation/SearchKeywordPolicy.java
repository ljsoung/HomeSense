package com.jiseong.homesense.common.validation;

import com.jiseong.homesense.common.exception.InvalidSearchKeywordException;

/**
 * 검색어 입력 규칙. 단지 검색의 keyword 필터(API-CPX-01)와 검색 기록(POST /api/search/logs,
 * API-SEARCH-01)이 반드시 같은 규칙을 써야 한다 — 기록은 되는데 검색은 400이 나거나 그 반대가 되면
 * 인기검색어 칩이 결과 없는 검색어를 노출하게 된다.
 *
 * <p>앞뒤 공백은 제거하고, 길이는 코드포인트 기준 2~50자다({@code NicknameValidator}와 같은 기준 —
 * 이모지 1개를 2자로 세지 않는다).
 */
public final class SearchKeywordPolicy {

    public static final int MIN_LENGTH = 2;
    public static final int MAX_LENGTH = 50;

    private SearchKeywordPolicy() {
    }

    /** 검색 필터용 — null/공백만이면 "조건 없음"(null), 그 외엔 trim 후 길이를 검사한다. */
    public static String normalizeOptional(String raw) {
        if (raw == null || raw.isBlank()) {
            return null;
        }
        return checkLength(raw.trim());
    }

    /** 검색 기록용 — 기록할 검색어가 없으면 400이다. */
    public static String normalizeRequired(String raw) {
        String normalized = normalizeOptional(raw);
        if (normalized == null) {
            throw new InvalidSearchKeywordException();
        }
        return normalized;
    }

    private static String checkLength(String trimmed) {
        int length = trimmed.codePointCount(0, trimmed.length());
        if (length < MIN_LENGTH || length > MAX_LENGTH) {
            throw new InvalidSearchKeywordException();
        }
        return trimmed;
    }
}
