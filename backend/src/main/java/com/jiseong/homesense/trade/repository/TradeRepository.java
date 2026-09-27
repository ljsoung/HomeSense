package com.jiseong.homesense.trade.repository;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.jiseong.homesense.trade.entity.Trade;

public interface TradeRepository extends JpaRepository<Trade, Long>, TradeRepositoryCustom {

    Optional<Trade> findByDedupHash(String dedupHash);

    /**
     * BAT-LOD-01(TradeChunkLoader) 전용 원자적 upsert. "조회 → 없으면 INSERT, 있으면 UPDATE, 경쟁 시
     * 재조회 후 재시도"(TradeInsertGateway로 INSERT만 REQUIRES_NEW 격리)로 구현했던 이전 버전은
     * SVC-NTF-01의 NotificationSettingRepository#upsert가 이미 겪은 것과 같은 함정이 있었다 — MariaDB
     * 기본 격리수준(REPEATABLE READ)에서는 INSERT 실패 후 같은(바깥) 청크 트랜잭션의 재조회가 그
     * 트랜잭션이 이미 확립한 스냅샷에 묶여, 경쟁에서 이긴 다른 트랜잭션(또는 같은 청크 안에서 먼저
     * 처리된 다른 항목)의 커밋을 여전히 보지 못한다. 이 프로젝트 코드에는 진짜 동시 배치 실행이 없지만
     * (BAT-SCH-01이 항상 단일 스레드로 조합을 순회), data.go.kr 페이지네이션이 응답 페이지 사이에
     * 같은 거래를 중복으로 돌려주면(예: 당월 데이터가 계속 갱신되는 도중 페이지를 나눠 조회) 같은 청크
     * 트랜잭션 안에서 같은 dedup_hash가 두 번 등장해 정확히 이 문제가 재현된다 — 실제 로컬 배치
     * 재실행에서 처리 대상의 2.2%(150,485건 중 3,352건)가 이 경로로 유실됨을 실측 확인했다(2026-09-15).
     * {@code INSERT ... ON DUPLICATE KEY UPDATE}는 단일 원자적 SQL 문장이라 이 스냅샷 문제 자체가
     * 발생하지 않는다 — 별도 조회·재시도·REQUIRES_NEW 격리(TradeInsertGateway)가 전부 불필요해져
     * 삭제했다.
     *
     * <p>ON DUPLICATE KEY UPDATE는 {@code Trade.applyLateUpdate()}가 갱신하던 필드(cancel_yn/
     * cancel_date/registration_date/apt_dong)만 그대로 갱신한다 — complex_id/legal_dong_cd/
     * match_method 등 매칭 결과나 dedup_hash 자체는 최초 적재 시점 값을 유지한다(재매칭은 이 upsert의
     * 책임이 아니다, 기존 applyLateUpdate() javadoc과 동일한 원칙).
     *
     * @return JDBC가 보고하는 영향받은 행 수. MariaDB Connector/J의 {@code useAffectedRows} 설정에
     *         따라 정확한 의미가 달라진다(true면 INSERT=1/값이 바뀐 UPDATE=2/값이 안 바뀐 UPDATE=0,
     *         false면 매칭된 행 수로 INSERT·UPDATE 모두 1) — 이 프로젝트 데이터소스 URL은 이 옵션을
     *         명시하지 않아 드라이버 기본값을 따른다. {@code TradeChunkLoader}는 이 값을 inserted/
     *         updated 로그 집계에만 참고용으로 쓴다(batch_log에는 둘의 합인 processedCount만 기록되므로
     *         이 구분이 부정확해도 적재 정확성 자체에는 영향이 없다).
     */
    @Modifying
    @Query(value = """
            INSERT INTO trade
                (housing_type, deal_category, rent_type, dataset_id, sgg_cd, legal_dong_cd, umd_nm,
                 complex_id, building_name, jibun, exclu_use_area, floor, build_year, deal_date,
                 deal_amount, deposit_amount, monthly_rent_amount, apt_dong, dealing_type, agent_sgg_nm,
                 registration_date, seller_type, buyer_type, land_lease_yn, cancel_yn, cancel_date,
                 match_method, match_confidence, dedup_hash, created_at, updated_at)
            VALUES
                (:housingType, :dealCategory, :rentType, :datasetId, :sggCd, :legalDongCd, :umdNm,
                 :complexId, :buildingName, :jibun, :excluUseArea, :floor, :buildYear, :dealDate,
                 :dealAmount, :depositAmount, :monthlyRentAmount, :aptDong, :dealingType, :agentSggNm,
                 :registrationDate, :sellerType, :buyerType, :landLeaseYn, :cancelYn, :cancelDate,
                 :matchMethod, :matchConfidence, :dedupHash, :now, :now)
            ON DUPLICATE KEY UPDATE
                cancel_yn = VALUES(cancel_yn),
                cancel_date = VALUES(cancel_date),
                registration_date = VALUES(registration_date),
                apt_dong = VALUES(apt_dong),
                updated_at = VALUES(updated_at)
            """, nativeQuery = true)
    int upsert(
            @Param("housingType") String housingType,
            @Param("dealCategory") String dealCategory,
            @Param("rentType") String rentType,
            @Param("datasetId") String datasetId,
            @Param("sggCd") String sggCd,
            @Param("legalDongCd") String legalDongCd,
            @Param("umdNm") String umdNm,
            @Param("complexId") Long complexId,
            @Param("buildingName") String buildingName,
            @Param("jibun") String jibun,
            @Param("excluUseArea") BigDecimal excluUseArea,
            @Param("floor") Short floor,
            @Param("buildYear") Short buildYear,
            @Param("dealDate") LocalDate dealDate,
            @Param("dealAmount") Long dealAmount,
            @Param("depositAmount") Long depositAmount,
            @Param("monthlyRentAmount") Long monthlyRentAmount,
            @Param("aptDong") String aptDong,
            @Param("dealingType") String dealingType,
            @Param("agentSggNm") String agentSggNm,
            @Param("registrationDate") LocalDate registrationDate,
            @Param("sellerType") String sellerType,
            @Param("buyerType") String buyerType,
            @Param("landLeaseYn") Boolean landLeaseYn,
            @Param("cancelYn") boolean cancelYn,
            @Param("cancelDate") LocalDate cancelDate,
            @Param("matchMethod") String matchMethod,
            @Param("matchConfidence") BigDecimal matchConfidence,
            @Param("dedupHash") String dedupHash,
            @Param("now") LocalDateTime now);

    Page<Trade> findByComplex_ComplexId(Long complexId, Pageable pageable);

    /**
     * BAT-MAT-02 재매칭 전용(TradeRematchRunner) 커서 — legal_dong_cd는 있지만(BAT-MAT-01 성공)
     * complex_id는 없는(BAT-MAT-02 실패) 행만 골라 trade_id 오름차순으로 순회한다. 오프셋 기반
     * 페이지네이션 대신 trade_id 커서를 쓰는 이유: 이번 순회에서 새로 매칭에 성공한 행은 WHERE
     * 조건(complex IS NULL)에서 곧바로 빠지므로, 오프셋을 그대로 증가시키면 이미 앞에서 빠져나간
     * 행들 때문에 남은 미매칭 행 일부를 건너뛰게 된다 — trade_id 기준 커서는 매칭 성공 여부와
     * 무관하게 항상 앞으로만 전진해 이 문제가 없다.
     */
    List<Trade> findByComplexIsNullAndLegalDistrictCodeIsNotNullAndTradeIdGreaterThanOrderByTradeIdAsc(
            Long tradeId, Pageable pageable);

    /**
     * BAT-MAT-02 재매칭 2차 패스(TradeRematchRunner) 커서 — complex_id 유무와 무관하게 {@code cutoff}
     * 이전에 매칭(또는 최초 적재)된 행 전부를 대상으로 삼는다. 1차 패스(complex_id IS NULL)와 달리
     * 이미 SIMILAR/EXACT로 배정된 행도 포함해야, 매처 로직이 바뀌기 전 오배정(예: 버그 C 수정 전
     * SIMILAR로 잘못 채택된 행이 지번 비교가 살아난 뒤 EXACT로 재배정돼야 하는 경우)까지 잡을 수 있다.
     */
    List<Trade> findByLegalDistrictCodeIsNotNullAndUpdatedAtBeforeAndTradeIdGreaterThanOrderByTradeIdAsc(
            LocalDateTime cutoff, Long tradeId, Pageable pageable);

    /**
     * BAT-MAT-02 dedup_hash 전수 복구 커서(TradeRematchRunner.repairDedupHashes()) — 매칭 여부와
     * 무관하게 테이블 전체를 trade_id 오름차순으로 순회한다.
     */
    List<Trade> findByTradeIdGreaterThanOrderByTradeIdAsc(Long tradeId, Pageable pageable);

    /**
     * BAT-MAT-02 재매칭 전용(TradeRematchRunner) — {@link #upsert}가 의도적으로 보존하는 매칭
     * 필드(complex_id/match_method/match_confidence)를, 매처 로직이 수정된 뒤 이미 적재된 stale
     * 미매칭 행에 한해 명시적으로 갱신한다. 일반 수집 경로(upsert)와 완전히 분리된 별도 메서드로 둬,
     * "재매칭은 upsert의 책임이 아니다"는 기존 설계를 건드리지 않는다.
     *
     * <p>{@code dedupHash}도 함께 갱신한다(Codex 코드리뷰 P1 지적) — {@code DedupHashCalculator}는
     * 매칭 성공 건이면 complex_id를, 실패 건이면 "UNMATCHED|sggCd|umdNm|buildingName|jibun"을 식별자로
     * 쓴다({@code DedupHashCalculator} javadoc 참고). complex_id만 바꾸고 dedup_hash를 그대로 두면,
     * 다음 정상 수집(BAT-SCH-01) 때 같은 실거래를 다시 파싱한 draft는 새 complex_id 기준 해시를 계산해
     * 이 행의 저장된(옛) 해시와 달라진다 — {@link #upsert}의 UNIQUE 제약 매칭이 빗나가 완전히 새로운
     * 행으로 INSERT되며 같은 실거래가 두 행으로 중복된다. {@code TradeRematchBatchProcessor}가 이
     * 메서드를 호출하기 전에 새 dedup_hash를 직접 계산해 넘긴다.
     */
    @Modifying
    @Query(value = """
            UPDATE trade
            SET complex_id = :complexId, match_method = :matchMethod, match_confidence = :matchConfidence,
                dedup_hash = :dedupHash, updated_at = :now
            WHERE trade_id = :tradeId
            """, nativeQuery = true)
    int applyRematch(
            @Param("tradeId") Long tradeId,
            @Param("complexId") Long complexId,
            @Param("matchMethod") String matchMethod,
            @Param("matchConfidence") BigDecimal matchConfidence,
            @Param("dedupHash") String dedupHash,
            @Param("now") LocalDateTime now);

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

    /**
     * complex.legal_dong_cd 백필 교차검증용 — 단지별로 연결된 거래의 legal_dong_cd 분포. trade의
     * 법정동코드는 Open API 응답(sggCd+umdNm) 기반이라 단지 주소 텍스트와 독립된 대조군이 된다.
     * [complex_id, legal_dong_cd, 건수]
     */
    @Query(value = """
            SELECT complex_id, legal_dong_cd, COUNT(*) FROM trade
            WHERE complex_id IS NOT NULL AND legal_dong_cd IS NOT NULL
            GROUP BY complex_id, legal_dong_cd
            """, nativeQuery = true)
    List<Object[]> countLegalDongCdByComplex();
}
