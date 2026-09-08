package com.example.ioedunew.ai.skill;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

public interface AiRunRepository extends JpaRepository<AiRun, Long> {
    Page<AiRun> findAllByOrderByIdDesc(Pageable pageable);

    Page<AiRun> findByUserIdOrderByIdDesc(Long userId, Pageable pageable);
}
