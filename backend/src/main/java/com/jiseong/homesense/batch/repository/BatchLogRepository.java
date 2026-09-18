package com.jiseong.homesense.batch.repository;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import com.jiseong.homesense.batch.entity.BatchLog;

public interface BatchLogRepository extends JpaRepository<BatchLog, Long> {

    List<BatchLog> findBySuccessYnFalseOrderByStartedAtDesc();

    /**
     * lawd_cd 커버리지 공백 소급 수집(백필) 대상을 하드코딩 없이 도출하기 위한 조회다 — 지금까지
     * BAT-SCH-01이 실제로 순회한 적 있는 lawd_cd 전체(= 재적재 이전 활성 코드 집합과 사실상 동일)를
     * 반환한다. {@code legalDistrictCodeRepository.findDistinctActiveSggCd()}(현재 활성 코드)와의
     * 차집합을 취하면, legal_district_code 재적재로 새로 활성화됐지만 정규 배치가 한 번도 순회한 적
     * 없는 코드만 정확히 걸러진다 — 재적재 시점의 "이전 목록" 스냅샷을 별도로 보관하지 않아도 된다.
     */
    @Query("SELECT DISTINCT b.lawdCd FROM BatchLog b")
    List<String> findDistinctLawdCd();
}
