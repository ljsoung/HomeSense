package com.jiseong.homesense.favorite.repository;

import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;

import com.jiseong.homesense.favorite.entity.FavoriteProperty;

public interface FavoritePropertyRepository extends JpaRepository<FavoriteProperty, Long> {

    List<FavoriteProperty> findByUser_UserId(Long userId);

    Optional<FavoriteProperty> findByUser_UserIdAndComplex_ComplexId(Long userId, Long complexId);

    boolean existsByUser_UserIdAndComplex_ComplexId(Long userId, Long complexId);
}
