package com.jiseong.homesense.trade.repository;

import java.util.Optional;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import com.jiseong.homesense.trade.entity.Trade;

public interface TradeRepository extends JpaRepository<Trade, Long> {

    Optional<Trade> findByDedupHash(String dedupHash);

    Page<Trade> findByComplex_ComplexId(Long complexId, Pageable pageable);
}
