package com.jiseong.homesense.complex.repository;

import java.util.List;
import java.util.Optional;

import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import com.jiseong.homesense.complex.entity.Complex;

public interface ComplexRepository extends JpaRepository<Complex, Long>, ComplexRepositoryCustom {

    Optional<Complex> findBySourceComplexCd(String sourceComplexCd);

    /**
     * BAT-MAT-02 1차 필터링(idx_complex_region)에 쓰는 시도/시군구/동리 완전일치 조회.
     */
    List<Complex> findBySidoAndSigunguAndDongRi(String sido, String sigungu, String dongRi);

    /**
     * SVC-CPX-01.getPopular() fallback — 최근 거래량만으로 limit을 못 채울 때 DB에 가장 최근 등록된
     * 단지로 나머지를 채운다(UI정의서 5.1절 HOME-01 "최신 등록 단지" 대체 노출). complex_id(서로게이트
     * PK, AUTO_INCREMENT)를 쓴다 — data_updated_at은 원본 xlsx 스냅샷 기준일이라 단지 기본정보가
     * 파일 1건을 통째로 적재한 지금은 사실상 전체가 같은 값이라 정렬 기준으로 무의미하다(코드리뷰에서
     * 지적됨, 지성 확인).
     */
    List<Complex> findAllByOrderByComplexIdDesc(Pageable pageable);
}
