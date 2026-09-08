package com.example.ioedunew.ai.skill;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface AiToolPolicyRepository extends JpaRepository<AiToolPolicy, Long> {
    Optional<AiToolPolicy> findByToolName(String toolName);
}
