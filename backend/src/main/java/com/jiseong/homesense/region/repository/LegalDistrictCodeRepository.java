package com.jiseong.homesense.region.repository;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;

import com.jiseong.homesense.region.entity.LegalDistrictCode;

public interface LegalDistrictCodeRepository extends JpaRepository<LegalDistrictCode, String> {

    List<LegalDistrictCode> findBySidoNameAndSigunguNameAndEupmyeondongNameAndIsActiveTrue(
            String sidoName, String sigunguName, String eupmyeondongName);

    List<LegalDistrictCode> findByLegalDongCdStartingWithAndIsActiveTrue(String legalDongCdPrefix);

    /**
     * 현재 활성 행을 전부 비활성화한다. 이번 CSV에 실제로 존재하는 행만 뒤이은 upsert로 다시 활성화되므로,
     * dataVersion(날짜, 일 단위 해상도)에 기대지 않고도 같은 날 재적재를 정확히 처리할 수 있다.
     */
    @Modifying
    @Query("UPDATE LegalDistrictCode c SET c.isActive = false WHERE c.isActive = true")
    int deactivateAll();
}
