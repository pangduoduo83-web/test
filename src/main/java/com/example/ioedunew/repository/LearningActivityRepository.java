package com.example.ioedunew.repository;

import com.example.ioedunew.entity.LearningActivity;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.LocalDateTime;
import java.util.List;

public interface LearningActivityRepository extends JpaRepository<LearningActivity, Long> {
    List<LearningActivity> findByUserIdAndCreatedAtAfterOrderByCreatedAtDesc(Long userId, LocalDateTime after);

    List<LearningActivity> findTop10ByUserIdOrderByCreatedAtDesc(Long userId);

    java.util.Optional<LearningActivity> findTopByUserIdOrderByCreatedAtDesc(Long userId);

    List<LearningActivity> findByCreatedAtAfterOrderByCreatedAtDesc(LocalDateTime after);

    List<LearningActivity> findTop30ByOrderByCreatedAtDesc();

    void deleteByUserId(Long userId);
}
