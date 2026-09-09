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

public interface TradeRepository extends JpaRepository<Trade, Long>, TradeRepositoryCustom {

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

    /**
     * SVC-RGN-01.getInterestSummary() — RegionStatsCalculator가 법정동코드 하나의 기간별 매매(SALE)
     * 평균가를 구할 때 쓴다. 보증금(RENT)은 매매가와 금액 구조가 달라 대상에서 제외한다(SVC-CPX-01/
     * TRD-01의 기존 "금액" 결정과 같은 이유, CLAUDE.md 참고). 취소된 거래도 제외한다. 대상 기간에
     * 해당 거래가 하나도 없으면 SQL AVG는 NULL을 내므로 Optional.empty()로 매핑된다.
     */
    @Query("""
            SELECT AVG(t.dealAmount)
            FROM Trade t
            WHERE t.legalDistrictCode.legalDongCd = :legalDongCd
              AND t.dealCategory = com.jiseong.homesense.trade.entity.DealCategory.SALE
              AND t.cancelYn = false
              AND t.dealDate >= :from AND t.dealDate < :to
            """)
    Optional<Double> findAverageSaleAmount(@Param("legalDongCd") String legalDongCd,
            @Param("from") LocalDate from, @Param("to") LocalDate to);

    /**
     * SVC-FAV-01.getFavoriteRegions() — MY-02가 요구하는 "3.3㎡당 평균가"(전용면적 정규화, CLAUDE.md
     * SVC-RGN-01 절의 "완결 필요" 항목). 거래 1건마다 평당가(dealAmount / (excluUseArea / 3.3058))로
     * 정규화한 뒤 그 값들을 단순 평균한다 — 합계/합계(면적 가중 평균) 방식이 아니라 거래 단위 평균을
     * 택했다: 특정 평형 거래 몇 건이 합계 방식에서 과대 대표되는 것을 피하고, 국내 부동산 서비스의
     * "평당가 평균" 표현이 통상 거래 단위 평균을 가리키는 관행과도 맞는다(지성 확인 필요). 모집단
     * 정의(매매·미취소·기간 내)는 findAverageSaleAmount()와 동일하다.
     */
    @Query("""
            SELECT AVG(t.dealAmount / (t.excluUseArea / 3.3058))
            FROM Trade t
            WHERE t.legalDistrictCode.legalDongCd = :legalDongCd
              AND t.dealCategory = com.jiseong.homesense.trade.entity.DealCategory.SALE
              AND t.cancelYn = false
              AND t.dealDate >= :from AND t.dealDate < :to
            """)
    Optional<Double> findAveragePricePerPyeongForSale(@Param("legalDongCd") String legalDongCd,
            @Param("from") LocalDate from, @Param("to") LocalDate to);

    /**
     * SVC-FAV-01.getFavoriteRegions() — MY-02가 요구하는 "신규거래 건수". findAverageSaleAmount()와
     * 같은 모집단(매매·미취소·기간 내)의 건수를 센다.
     */
    @Query("""
            SELECT COUNT(t)
            FROM Trade t
            WHERE t.legalDistrictCode.legalDongCd = :legalDongCd
              AND t.dealCategory = com.jiseong.homesense.trade.entity.DealCategory.SALE
              AND t.cancelYn = false
              AND t.dealDate >= :from AND t.dealDate < :to
            """)
    long countSaleTrades(@Param("legalDongCd") String legalDongCd,
            @Param("from") LocalDate from, @Param("to") LocalDate to);

    /**
     * SVC-FAV-01.getFavoriteProperties() — 관심 매물의 "전월 대비 변동률"을 단지 단위로 계산한다.
     * findAverageSaleAmount()와 같은 모집단 정의(매매·미취소·기간 내)를 legal_dong_cd 대신
     * complex_id로 좁힌 버전이다.
     */
    @Query("""
            SELECT AVG(t.dealAmount)
            FROM Trade t
            WHERE t.complex.complexId = :complexId
              AND t.dealCategory = com.jiseong.homesense.trade.entity.DealCategory.SALE
              AND t.cancelYn = false
              AND t.dealDate >= :from AND t.dealDate < :to
            """)
    Optional<Double> findAverageSaleAmountByComplex(@Param("complexId") Long complexId,
            @Param("from") LocalDate from, @Param("to") LocalDate to);

    /**
     * SVC-FAV-01.getFavoriteProperties() 배치 버전 — findAverageSaleAmountByComplex()를 관심 매물
     * 개수만큼 반복 호출하는 대신, 대상 complex_id 전체를 한 번의 GROUP BY 쿼리로 집계한다(N+1 제거,
     * Codex 코드리뷰 P2 지적). 각 행은 [complexId(Long), avgDealAmount(Double)]이다 — 거래가 없는
     * complex_id는 결과 행 자체가 없으므로 호출부에서 Map으로 모은 뒤 없는 키를 null로 취급해야 한다.
     */
    @Query("""
            SELECT t.complex.complexId, AVG(t.dealAmount)
            FROM Trade t
            WHERE t.complex.complexId IN :complexIds
              AND t.dealCategory = com.jiseong.homesense.trade.entity.DealCategory.SALE
              AND t.cancelYn = false
              AND t.dealDate >= :from AND t.dealDate < :to
            GROUP BY t.complex.complexId
            """)
    List<Object[]> findAverageSaleAmountGroupedByComplex(@Param("complexIds") List<Long> complexIds,
            @Param("from") LocalDate from, @Param("to") LocalDate to);

    /** SVC-FAV-01.getFavoriteRegions() 배치 버전 — findAverageSaleAmount()의 GROUP BY 집계판. */
    @Query("""
            SELECT t.legalDistrictCode.legalDongCd, AVG(t.dealAmount)
            FROM Trade t
            WHERE t.legalDistrictCode.legalDongCd IN :legalDongCds
              AND t.dealCategory = com.jiseong.homesense.trade.entity.DealCategory.SALE
              AND t.cancelYn = false
              AND t.dealDate >= :from AND t.dealDate < :to
            GROUP BY t.legalDistrictCode.legalDongCd
            """)
    List<Object[]> findAverageSaleAmountGroupedByLegalDongCd(@Param("legalDongCds") List<String> legalDongCds,
            @Param("from") LocalDate from, @Param("to") LocalDate to);

    /** SVC-FAV-01.getFavoriteRegions() 배치 버전 — findAveragePricePerPyeongForSale()의 GROUP BY 집계판. */
    @Query("""
            SELECT t.legalDistrictCode.legalDongCd, AVG(t.dealAmount / (t.excluUseArea / 3.3058))
            FROM Trade t
            WHERE t.legalDistrictCode.legalDongCd IN :legalDongCds
              AND t.dealCategory = com.jiseong.homesense.trade.entity.DealCategory.SALE
              AND t.cancelYn = false
              AND t.dealDate >= :from AND t.dealDate < :to
            GROUP BY t.legalDistrictCode.legalDongCd
            """)
    List<Object[]> findAveragePricePerPyeongGroupedByLegalDongCd(@Param("legalDongCds") List<String> legalDongCds,
            @Param("from") LocalDate from, @Param("to") LocalDate to);

    /** SVC-FAV-01.getFavoriteRegions() 배치 버전 — countSaleTrades()의 GROUP BY 집계판. */
    @Query("""
            SELECT t.legalDistrictCode.legalDongCd, COUNT(t)
            FROM Trade t
            WHERE t.legalDistrictCode.legalDongCd IN :legalDongCds
              AND t.dealCategory = com.jiseong.homesense.trade.entity.DealCategory.SALE
              AND t.cancelYn = false
              AND t.dealDate >= :from AND t.dealDate < :to
            GROUP BY t.legalDistrictCode.legalDongCd
            """)
    List<Object[]> countSaleTradesGroupedByLegalDongCd(@Param("legalDongCds") List<String> legalDongCds,
            @Param("from") LocalDate from, @Param("to") LocalDate to);
}
