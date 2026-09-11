package com.jiseong.homesense.search.controller;

import java.util.List;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.jiseong.homesense.common.response.ApiResponse;
import com.jiseong.homesense.search.dto.PopularKeywordResponse;
import com.jiseong.homesense.search.exception.InvalidLimitException;
import com.jiseong.homesense.search.service.SearchService;

import lombok.RequiredArgsConstructor;

/**
 * API-SEARCH-01(신규 제안, 반영 전 검토 필요 — CLAUDE.md 참고). base path: /api/search. 비로그인 조회
 * 허용(인증 불필요) — 검색 실행 기록(record())은 이 컨트롤러에 노출하지 않는다(CLAUDE.md 참고,
 * SVC-CPX-01.search() 내부에서만 호출됨).
 */
@RestController
@RequestMapping("/api/search")
@RequiredArgsConstructor
public class SearchController {

    private static final int DEFAULT_LIMIT = 5;

    /** ComplexController.MIN/MAX_POPULAR_LIMIT과 같은 이유(0 이하 방어 + 자원 고갈 방지)의 상한 가드. */
    private static final int MIN_LIMIT = 1;
    private static final int MAX_LIMIT = 20;

    private final SearchService searchService;

    @GetMapping("/popular")
    public ApiResponse<List<PopularKeywordResponse>> popular(
            @RequestParam(defaultValue = "" + DEFAULT_LIMIT) int limit) {
        if (limit < MIN_LIMIT || limit > MAX_LIMIT) {
            throw new InvalidLimitException();
        }
        return ApiResponse.success(searchService.getPopularKeywords(limit));
    }
}
