package com.jiseong.homesense.favorite.repository;

import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.jiseong.homesense.favorite.entity.FavoriteRegion;

public interface FavoriteRegionRepository extends JpaRepository<FavoriteRegion, Long> {

    /**
     * SVC-FAV-01.getFavoriteRegions() — legalDistrictCode를 JOIN FETCH해 목록 매핑 중 관심 지역
     * 개수만큼 legalDistrictCode를 지연 로딩하는 N+1을 피한다(Codex 코드리뷰 P2 지적).
     */
    @Query("SELECT f FROM FavoriteRegion f JOIN FETCH f.legalDistrictCode WHERE f.user.userId = :userId")
    List<FavoriteRegion> findByUser_UserId(@Param("userId") Long userId);

    Optional<FavoriteRegion> findByUser_UserIdAndLegalDistrictCode_LegalDongCd(Long userId, String legalDongCd);

    boolean existsByUser_UserIdAndLegalDistrictCode_LegalDongCd(Long userId, String legalDongCd);
}
