package com.example.ioeduhub.repository;

import com.example.ioeduhub.entity.HubReviewLog;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface ReviewLogRepo extends JpaRepository<HubReviewLog, Long> {
    List<HubReviewLog> findByItemIdOrderByReviewedAtDesc(Long itemId);
}
