package com.jiseong.homesense.complex.repository;

import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;

import com.jiseong.homesense.complex.entity.Complex;

public interface ComplexRepository extends JpaRepository<Complex, Long> {

    Optional<Complex> findBySourceComplexCd(String sourceComplexCd);

    /**
     * BAT-MAT-02 1차 필터링(idx_complex_region)에 쓰는 시도/시군구/동리 완전일치 조회.
     */
    List<Complex> findBySidoAndSigunguAndDongRi(String sido, String sigungu, String dongRi);
}
