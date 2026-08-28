package com.jiseong.homesense.complex.repository;

import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;

import com.jiseong.homesense.complex.entity.Complex;

public interface ComplexRepository extends JpaRepository<Complex, Long> {

    Optional<Complex> findBySourceComplexCd(String sourceComplexCd);
}
