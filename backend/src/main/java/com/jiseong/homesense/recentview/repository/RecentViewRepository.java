package com.jiseong.homesense.recentview.repository;

import java.util.List;
import java.util.Optional;

import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import com.jiseong.homesense.recentview.entity.RecentView;

public interface RecentViewRepository extends JpaRepository<RecentView, Long> {

    List<RecentView> findByUser_UserIdOrderByViewedAtDesc(Long userId, Pageable pageable);

    List<RecentView> findBySessionIdOrderByViewedAtDesc(String sessionId, Pageable pageable);

    Optional<RecentView> findByUser_UserIdAndComplex_ComplexId(Long userId, Long complexId);

    Optional<RecentView> findBySessionIdAndComplex_ComplexId(String sessionId, Long complexId);

    long countByUser_UserId(Long userId);

    long countBySessionId(String sessionId);

    /** record()가 주체별 보관 건수 상한을 넘겼을 때 지울 가장 오래된 레코드를 고르는 데 쓴다. */
    List<RecentView> findByUser_UserIdOrderByViewedAtAsc(Long userId, Pageable pageable);

    List<RecentView> findBySessionIdOrderByViewedAtAsc(String sessionId, Pageable pageable);
}
