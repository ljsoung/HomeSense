package com.jiseong.homesense.recentview.repository;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;

import com.jiseong.homesense.recentview.entity.RecentView;

public interface RecentViewRepository extends JpaRepository<RecentView, Long> {

    List<RecentView> findByUser_UserIdOrderByViewedAtDesc(Long userId);

    List<RecentView> findBySessionIdOrderByViewedAtDesc(String sessionId);
}
