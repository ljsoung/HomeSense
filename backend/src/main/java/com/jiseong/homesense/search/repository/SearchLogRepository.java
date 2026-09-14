package com.jiseong.homesense.search.repository;

import java.time.LocalDateTime;
import java.util.List;

import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.jiseong.homesense.search.entity.SearchLog;

public interface SearchLogRepository extends JpaRepository<SearchLog, Long> {

    /**
     * SVC-SEARCH-01.getPopularKeywords() — since 이후 검색 빈도가 높은 순으로 keyword를 반환한다.
     * TradeRepository.findTopComplexIdsByRecentTradeVolume()과 같은 구조(고정 JPQL, GROUP BY +
     * Pageable로 LIMIT만 적용) — 동적 조건이 없는 고정 쿼리라 전용 MariaDB IT 없이 SearchServiceTest
     * (Mockito)로만 검증한다(CLAUDE.md SVC-RGN-01 절의 findAverageSaleAmount()와 같은 판단 기준).
     */
    @Query("""
            SELECT s.keyword
            FROM SearchLog s
            WHERE s.searchedAt >= :since
            GROUP BY s.keyword
            ORDER BY COUNT(s) DESC
            """)
    List<String> findTopKeywordsSince(@Param("since") LocalDateTime since, Pageable pageable);
}
