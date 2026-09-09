package com.jiseong.homesense.region.repository;

import java.util.List;
import java.util.Optional;

import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.jiseong.homesense.region.entity.LegalDistrictCode;

public interface LegalDistrictCodeRepository extends JpaRepository<LegalDistrictCode, String> {

    List<LegalDistrictCode> findBySidoNameAndSigunguNameAndEupmyeondongNameAndIsActiveTrue(
            String sidoName, String sigunguName, String eupmyeondongName);

    List<LegalDistrictCode> findByLegalDongCdStartingWithAndIsActiveTrue(String legalDongCdPrefix);

    /**
     * SVC-FAV-01.addFavoriteRegion() 전용 — 등록 가능한(선택 가능한) 행만 허용한다.
     * findById()만으로는 비활성 코드나 시도/시군구 대표행(eupmyeondongName=null, 계층 상위 행)까지
     * 그대로 통과시켜, 어떤 거래에도 매칭되지 않아 통계가 항상 빈 관심 지역이 등록될 수 있다 —
     * searchByNameContaining()이 자동완성에서 거는 것과 같은 조건(isActive=true AND
     * eupmyeondongName IS NOT NULL)을 여기서도 동일하게 강제한다(Codex 코드리뷰 P2).
     */
    Optional<LegalDistrictCode> findByLegalDongCdAndIsActiveTrueAndEupmyeondongNameIsNotNull(String legalDongCd);

    /**
     * SVC-RGN-01.autocomplete() — 시도/시군구/읍면동명 부분일치 검색(idx_legal_district_region_name).
     * query 2자 미만 차단은 Service 쪽 책임이라 여기서는 검사하지 않는다.
     *
     * <p>{@code eupmyeondongName IS NOT NULL}로 시도/시군구 대표행(계층 상위 행, LegalDistrictCodeLoader가
     * 그 행들의 eupmyeondongName을 null로 채운다)을 제외하고 선택 가능한 읍면동(leaf) 행만 반환한다 —
     * LegalDistrictMatcher.matchByTradeSggCd()는 eupmyeondongName이 umdNm과 일치하는 행에만 거래를
     * 매칭시키므로, 대표행의 legal_dong_cd는 애초에 어떤 거래에도 연결될 수 없다. 이 필터 없이는 "강남구"
     * 같은 검색어가 그 구의 대표행(예: 1168000000)을 후보로 내놓고, 그 코드를 실거래 검색이나
     * getInterestSummary()에 넘기면 항상 0건이 되는 막다른 결과를 낳는다(Codex 코드리뷰 P1).
     */
    @Query("""
            SELECT c FROM LegalDistrictCode c
            WHERE c.isActive = true
              AND c.eupmyeondongName IS NOT NULL
              AND (c.sidoName LIKE CONCAT('%', :query, '%')
                OR c.sigunguName LIKE CONCAT('%', :query, '%')
                OR c.eupmyeondongName LIKE CONCAT('%', :query, '%'))
            ORDER BY c.legalDongCd
            """)
    List<LegalDistrictCode> searchByNameContaining(@Param("query") String query, Pageable pageable);

    /**
     * BAT-SCH-01 조합 순회의 시군구 축 — sgg_cd(legal_dong_cd 앞 5자리) distinct 목록(약 250여 개).
     */
    @Query("SELECT DISTINCT SUBSTRING(c.legalDongCd, 1, 5) FROM LegalDistrictCode c WHERE c.isActive = true")
    List<String> findDistinctActiveSggCd();

    /**
     * 현재 활성 행을 전부 비활성화한다. 이번 CSV에 실제로 존재하는 행만 뒤이은 upsert로 다시 활성화되므로,
     * dataVersion(날짜, 일 단위 해상도)에 기대지 않고도 같은 날 재적재를 정확히 처리할 수 있다.
     */
    @Modifying
    @Query("UPDATE LegalDistrictCode c SET c.isActive = false WHERE c.isActive = true")
    int deactivateAll();
}
