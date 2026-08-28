package com.jiseong.homesense.batch.repository;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;

import com.jiseong.homesense.batch.entity.BatchLog;

public interface BatchLogRepository extends JpaRepository<BatchLog, Long> {

    List<BatchLog> findBySuccessYnFalseOrderByStartedAtDesc();
}
