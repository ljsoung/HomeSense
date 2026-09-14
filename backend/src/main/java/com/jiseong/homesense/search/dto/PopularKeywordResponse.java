package com.jiseong.homesense.search.dto;

/** SVC-SEARCH-01.getPopularKeywords() 응답 항목 — 순위는 리스트 인덱스로 표현하고 별도 필드로 담지 않는다. */
public record PopularKeywordResponse(String keyword) {
}
