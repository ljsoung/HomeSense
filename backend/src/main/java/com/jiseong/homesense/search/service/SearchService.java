package com.jiseong.homesense.search.service;

import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.List;

import org.springframework.cache.annotation.Cacheable;
import org.springframework.data.domain.PageRequest;
import org.springframework.scheduling.annotation.Async;
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
 * <p>getPopularKeywords()는 Controller에 노출되고, record()는 SVC-CPX-01.search() 내부에서 호출되는
 * 내부 협력 메서드다(SVC-CPX-01.getDetail()이 SVC-RCV-01.record()를 호출하는 것과 같은 계층 협력
 * 패턴, 설계서 2.2절). 새 엔드포인트를 따로 두지 않는다 — 검색 실행 자체가 이미
 * {@code GET /api/complexes/search} 호출이므로 그 안에서 기록한다.
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
     * SVC-CPX-01.search()가 사용자의 검색 실행(SRCH-01로 이어지는 원문 keyword)마다 호출하는 내부
     * 협력 메서드다 — 자동완성 훑어보기, 필터만 변경하는 재요청은 이 메서드 호출 대상이 아니다(호출
     * 여부는 ComplexService가 판단).
     *
     * <p>설계서 원안은 "1차 버전은 동기 호출로 시작해도 무방"이라 적었지만, 실제로는
     * {@code @Async}가 아니면 안 된다 — ComplexService.search()는 클래스 레벨
     * {@code @Transactional(readOnly = true)} 안에서 실행되는데, record()를 같은 스레드에서
     * 동기 호출하면 기본 전파(REQUIRED)가 그 readOnly 트랜잭션에 그대로 합류해 이 메서드의 INSERT가
     * readOnly 트랜잭션(드라이버에 따라 커넥션 자체가 읽기 전용으로 표시됨) 안에서 실행되는 문제가
     * 생긴다. SVC-RCV-01.record()가 {@code @Async}인 것과 같은 이유(응답 경로에 DB write를 얹지
     * 않는다, NFR-1)에 더해, 이 메서드는 그 이유 하나만으로도 비동기가 필수다 — 별도 스레드로
     * 넘어가면 호출자의 트랜잭션 컨텍스트를 상속받지 않아 이 문제 자체가 발생하지 않는다.
     * 로깅 실패가 검색 자체를 실패시키면 안 되므로 예외를 삼키고 COM-LOG-01(SLF4J)로만 남긴다 —
     * 예외가 나도 기본 {@code SimpleAsyncUncaughtExceptionHandler}가 로그만 남기고 호출자에게
     * 전파되지 않는다(AsyncConfig 참고).
     */
    @Async
    @Transactional
    public void record(String keyword) {
        if (keyword == null || keyword.isBlank()) {
            return;
        }
        try {
            searchLogRepository.save(SearchLog.record(keyword.trim()));
        } catch (RuntimeException e) {
            log.error("SVC-SEARCH-01 검색어 기록 실패 — 검색 응답 자체에는 영향 없음: keyword={}", keyword, e);
        }
    }
}
