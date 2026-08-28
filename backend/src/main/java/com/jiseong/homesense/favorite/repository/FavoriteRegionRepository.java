package com.jiseong.homesense.favorite.repository;

import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;

import com.jiseong.homesense.favorite.entity.FavoriteRegion;

public interface FavoriteRegionRepository extends JpaRepository<FavoriteRegion, Long> {

    List<FavoriteRegion> findByUser_UserId(Long userId);

    Optional<FavoriteRegion> findByUser_UserIdAndLegalDistrictCode_LegalDongCd(Long userId, String legalDongCd);

    boolean existsByUser_UserIdAndLegalDistrictCode_LegalDongCd(Long userId, String legalDongCd);
}
