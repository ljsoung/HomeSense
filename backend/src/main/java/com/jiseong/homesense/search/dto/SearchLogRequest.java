package com.jiseong.homesense.search.dto;

/** POST /api/search/logs 바디. 검증은 SearchKeywordPolicy(단지 검색 keyword와 같은 규칙)가 한다. */
public record SearchLogRequest(String keyword) {
}
