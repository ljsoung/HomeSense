package com.jiseong.homesense.favorite.repository;

import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.jiseong.homesense.favorite.entity.FavoriteProperty;

public interface FavoritePropertyRepository extends JpaRepository<FavoriteProperty, Long> {

    /**
     * SVC-FAV-01.getFavoriteProperties() — complex를 JOIN FETCH해 목록 매핑 중 관심 매물 개수만큼
     * complex를 지연 로딩하는 N+1을 피한다(Codex 코드리뷰 P2 지적).
     */
    @Query("SELECT f FROM FavoriteProperty f JOIN FETCH f.complex WHERE f.user.userId = :userId")
    List<FavoriteProperty> findByUser_UserId(@Param("userId") Long userId);

    Optional<FavoriteProperty> findByUser_UserIdAndComplex_ComplexId(Long userId, Long complexId);

    boolean existsByUser_UserIdAndComplex_ComplexId(Long userId, Long complexId);
}
