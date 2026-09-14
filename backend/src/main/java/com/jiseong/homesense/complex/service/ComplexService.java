package com.jiseong.homesense.complex.service;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

import org.springframework.cache.annotation.Cacheable;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.jiseong.homesense.complex.dto.BoundsCondition;
import com.jiseong.homesense.complex.dto.ComplexDetailResponse;
import com.jiseong.homesense.complex.dto.ComplexMapPointResponse;
import com.jiseong.homesense.complex.dto.ComplexMapSearchResponse;
import com.jiseong.homesense.complex.dto.ComplexSearchCondition;
import com.jiseong.homesense.complex.dto.ComplexSummaryResponse;
import com.jiseong.homesense.complex.dto.MapFilterCondition;
import com.jiseong.homesense.complex.entity.Complex;
import com.jiseong.homesense.complex.repository.ComplexRepository;
import com.jiseong.homesense.recentview.dto.RecentViewTarget;
import com.jiseong.homesense.recentview.service.RecentViewService;
import com.jiseong.homesense.search.service.SearchService;
import com.jiseong.homesense.trade.entity.Trade;
import com.jiseong.homesense.trade.repository.TradeRepository;

import lombok.RequiredArgsConstructor;

/**
 * SVC-CPX-01. 단지 검색·인기단지·상세·지도 범위 조회를 담당한다. 조회 트래픽이 가장 높은 도메인이라
 * getDetail()/getPopular()에 COM-CACHE-01 캐시(complexDetailV2/popularComplexes)를 적용한다(TTL 24h)
 * — 무효화는 BAT-LOD-01이 발행하는
 * TradeCacheEvictionEvent를 CacheEvictionListener가 이미 구독하고 있어 별도 배선이 필요 없다.
 *
 * <p>설계서 3.6절("RCV 도메인과 협력")·2.2절("Service-to-Service 직접 호출을 허용") 그대로
 * getDetail() 내부에서 SVC-RCV-01.record()를 부른다. 다만 이 메서드 자체에 {@code @Cacheable}을
 * 걸면 캐시 히트마다 기록이 스킵되므로, 캐시 조회는 {@link ComplexDetailCache}라는 별도 빈으로
 * 분리했다 — 같은 클래스 안에 캐시 전용 메서드를 따로 둬도 self-invocation이라 프록시를 안 거쳐
 * {@code @Cacheable}이 무력화되기 때문이다(CLAUDE.md SVC-RCV-01 절 참고).
 *
 * <p>search()도 같은 계층 협력 패턴으로 SVC-SEARCH-01.record()를 호출한다(신규 제안, CLAUDE.md
 * API-SEARCH-01 절 참고) — 검색 실행마다 원문 keyword를 인기검색어 집계용으로 남긴다. record()가
 * {@code @Async}라 이 클래스의 {@code @Transactional(readOnly = true)} 경계와 무관하게 별도
 * 트랜잭션에서 실행된다(SearchService.record() 참고).
 */
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class ComplexService {

    /** getPopular() "인기" 정의 — 최근 이 기간 내 거래량 기준(지성 확인). */
    private static final int POPULARITY_WINDOW_MONTHS = 3;

    /** searchInBounds() 결과 상한 — 이를 넘으면 상한까지만 반환하고 truncated=true. */
    private static final int MAX_MAP_RESULTS = 500;

    private final ComplexRepository complexRepository;
    private final TradeRepository tradeRepository;
    private final ComplexDetailCache complexDetailCache;
    private final RecentViewService recentViewService;
    private final SearchService searchService;

    public Page<ComplexSummaryResponse> search(ComplexSearchCondition condition, Pageable pageable) {
        searchService.record(condition.keyword());
        return complexRepository.search(condition, pageable);
    }

    @Cacheable(cacheNames = "popularComplexes", key = "#limit")
    public List<ComplexSummaryResponse> getPopular(int limit) {
        LocalDate since = LocalDate.now().minusMonths(POPULARITY_WINDOW_MONTHS);
        List<Long> complexIds = new ArrayList<>(
                tradeRepository.findTopComplexIdsByRecentTradeVolume(since, PageRequest.of(0, limit)));

        if (complexIds.size() < limit) {
            complexIds.addAll(fallbackComplexIds(complexIds, limit));
        }

        return complexIds.stream()
                .map(this::buildSummary)
                .filter(Objects::nonNull)
                .toList();
    }

    /**
     * 최근 거래량만으로 limit을 못 채우면(서비스 초기 등 거래 데이터가 적은 경우) complex_id가 가장 큰
     * (=DB에 가장 최근 등록된) 단지로 나머지 자리만 채운다(지성 확인) — 이미 거래량 기준에 뽑힌 단지는
     * 제외한다. data_updated_at을 쓰지 않는 이유는 ComplexRepository.findAllByOrderByComplexIdDesc()
     * 참고.
     */
    private List<Long> fallbackComplexIds(List<Long> alreadySelected, int limit) {
        int needed = limit - alreadySelected.size();
        List<Complex> candidates = complexRepository.findAllByOrderByComplexIdDesc(
                PageRequest.of(0, needed + alreadySelected.size()));

        return candidates.stream()
                .map(Complex::getComplexId)
                .filter(id -> !alreadySelected.contains(id))
                .limit(needed)
                .toList();
    }

    /**
     * userId·sessionId는 조회 이력 기록 주체 판별에만 쓰인다 — 상세정보 자체는 이 값과 무관하게
     * {@link ComplexDetailCache}에서 캐시째로 내려온다(비로그인·회원 모두 같은 캐시 엔트리 공유).
     */
    public ComplexDetailResponse getDetail(Long complexId, Long userId, String sessionId) {
        ComplexDetailResponse detail = complexDetailCache.get(complexId);
        recentViewService.record(userId, sessionId, new RecentViewTarget(complexId, detail.housingType()));
        return detail;
    }

    public ComplexMapSearchResponse searchInBounds(BoundsCondition bounds, MapFilterCondition filter) {
        List<Complex> results = complexRepository.searchInBounds(bounds, filter, MAX_MAP_RESULTS + 1);

        boolean truncated = results.size() > MAX_MAP_RESULTS;
        List<ComplexMapPointResponse> points = results.stream()
                .limit(MAX_MAP_RESULTS)
                .map(ComplexMapPointResponse::from)
                .toList();

        return new ComplexMapSearchResponse(points, truncated);
    }

    /**
     * candidate complex_id 하나당 최대 2건(findById + 대표 거래 조회)의 쿼리를 낸다 — search()가
     * QueryDSL 상관 서브쿼리로 단지+대표거래를 한 번에 가져오는 것과 다른 N+1 구조다. limit이
     * 1~50으로 막혀 있고(ComplexController) popularComplexes 캐시(TTL 24h)로 캐시 미스 시에만
     * 발생해 지금 당장 문제는 아니지만, 알려진 기술부채다(Codex 코드리뷰 — CLAUDE.md SVC-CPX-01
     * 절 참고). 나중에 손볼 때는 search()처럼 QueryDSL 서브쿼리 하나로 통합하는 방향을 검토하라.
     */
    private ComplexSummaryResponse buildSummary(Long complexId) {
        Complex complex = complexRepository.findById(complexId).orElse(null);
        if (complex == null) {
            return null;
        }
        Trade representativeTrade = tradeRepository
                .findFirstByComplex_ComplexIdAndCancelYnFalseOrderByDealDateDesc(complexId)
                .orElse(null);
        if (representativeTrade == null) {
            return null;
        }
        return ComplexSummaryResponse.of(complex, representativeTrade);
    }
}
