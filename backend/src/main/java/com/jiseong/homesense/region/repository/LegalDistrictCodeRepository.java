package com.jiseong.homesense.region.repository;

import java.time.LocalDate;
import java.util.List;

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
     * 이번 재적재(dataVersion)에서 갱신되지 않은 기존 활성 행을 비활성화한다 — CSV에서 사라졌거나 폐지된 코드.
     */
    @Modifying
    @Query("UPDATE LegalDistrictCode c SET c.isActive = false "
            + "WHERE c.dataVersion < :dataVersion AND c.isActive = true")
    int deactivateStaleVersions(@Param("dataVersion") LocalDate dataVersion);
}
