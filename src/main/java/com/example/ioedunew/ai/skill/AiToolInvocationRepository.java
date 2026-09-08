package com.example.ioedunew.ai.skill;

import org.springframework.data.jpa.repository.JpaRepository;

import java.time.LocalDateTime;
import java.util.List;

public interface AiToolInvocationRepository extends JpaRepository<AiToolInvocation, Long> {
    List<AiToolInvocation> findByRunIdOrderByIdAsc(Long runId);

    long countByUserIdAndToolNameAndCreatedAtAfter(Long userId, String toolName, LocalDateTime after);
}
