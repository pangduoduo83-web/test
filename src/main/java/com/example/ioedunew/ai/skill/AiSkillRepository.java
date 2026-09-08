package com.example.ioedunew.ai.skill;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface AiSkillRepository extends JpaRepository<AiSkill, Long> {
    Optional<AiSkill> findBySkillKey(String skillKey);

    List<AiSkill> findByScopeOrderByUpdatedAtDesc(String scope);

    List<AiSkill> findByScopeAndOwnerUserIdOrderByUpdatedAtDesc(String scope, Long ownerUserId);

    List<AiSkill> findAllByOrderByUpdatedAtDesc();
}
