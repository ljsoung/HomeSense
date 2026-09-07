package com.jiseong.homesense.trade.repository;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.jiseong.homesense.trade.entity.Trade;

public interface TradeRepository extends JpaRepository<Trade, Long> {

    Optional<Trade> findByDedupHash(String dedupHash);

    Page<Trade> findByComplex_ComplexId(Long complexId, Pageable pageable);

    /** SVC-CPX-01.getDetail()/getPopular() 대표 거래 — 취소되지 않은 거래 중 가장 최근 1건. */
    Optional<Trade> findFirstByComplex_ComplexIdAndCancelYnFalseOrderByDealDateDesc(Long complexId);

    /**
     * SVC-CPX-01.getPopular() — "인기"를 최근 거래량으로 정의한다(지성 확인, CLAUDE.md SVC-CPX-01
     * 절 참고). since 이후 취소되지 않은 거래가 많은 단지 순으로 complex_id를 반환한다. Pageable로
     * LIMIT(상위 N건)만 적용하고 정렬은 이 쿼리 자체가 담당하므로 Pageable의 sort는 쓰지 않는다.
     */
    @Query("""
            SELECT t.complex.complexId
            FROM Trade t
            WHERE t.dealDate >= :since AND t.cancelYn = false AND t.complex IS NOT NULL
            GROUP BY t.complex.complexId
            ORDER BY COUNT(t) DESC
            """)
    List<Long> findTopComplexIdsByRecentTradeVolume(@Param("since") LocalDate since, Pageable pageable);
}
