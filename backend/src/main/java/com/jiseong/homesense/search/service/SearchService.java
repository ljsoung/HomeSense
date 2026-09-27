package com.jiseong.homesense.search.service;

import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.List;

import org.springframework.cache.annotation.Cacheable;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.jiseong.homesense.search.dto.PopularKeywordResponse;
import com.jiseong.homesense.search.entity.SearchLog;
import com.jiseong.homesense.search.repository.SearchLogRepository;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * SVC-SEARCH-01(신규 제안, 반영 전 검토 필요 — CLAUDE.md API-SEARCH-01 절 참고). 요구사항정의서·
 * UI정의서·프로그램설계서·프로그램목록서 어디에도 정의되어 있지 않은 신규 도메인이다 — HOME-01
 * 히어로 검색바를 하드코딩 없이 실제 API로 연동하기로 하면서 추가됐다.
 *
 * <p>getPopularKeywords()는 GET /api/search/popular, record()는 POST /api/search/logs가 부른다. 기록은
 * 원래 SVC-CPX-01.search() 안에서 했는데, 목록 조회(필터 변경·페이지 이동 포함)마다 기록돼 집계가
 * 부풀려질 수 있어 "검색 실행" 순간에만 프론트가 호출하는 별도 엔드포인트로 분리했다.
 */
@Slf4j
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class SearchService {

    private static final ZoneId KST = ZoneId.of("Asia/Seoul");

    /** "인기"의 집계 창 — 최근 이 기간 내 검색 빈도 기준(신규 제안, 지성 확인 필요). */
    private static final int WINDOW_DAYS = 7;

    private final SearchLogRepository searchLogRepository;

    @Cacheable(cacheNames = "popularKeywords", key = "#limit")
    public List<PopularKeywordResponse> getPopularKeywords(int limit) {
        LocalDateTime since = LocalDateTime.now(KST).minusDays(WINDOW_DAYS);
        return searchLogRepository.findTopKeywordsSince(since, PageRequest.of(0, limit)).stream()
                .map(PopularKeywordResponse::new)
                .toList();
    }

    /**
     * POST /api/search/logs — 사용자가 검색을 실행한 순간 1회 호출된다. keyword는 Controller가
     * {@link com.jiseong.homesense.common.validation.SearchKeywordPolicy}로 이미 trim·길이(2~50자) 검증을
     * 마친 값이라 search_log.keyword(VARCHAR(100))를 넘을 수 없다.
     *
     * <p>예전에는 SVC-CPX-01.search()가 readOnly 트랜잭션 안에서 이 메서드를 불러 {@code @Async}가
     * 필수였지만(readOnly 트랜잭션 합류 문제), 이제 이 메서드 자체가 요청의 목적이라 동기로 실행하고
     * 실패도 호출자에게 그대로 알린다.
     */
    @Transactional
    public void record(String keyword) {
        searchLogRepository.save(SearchLog.record(keyword));
    }
}
