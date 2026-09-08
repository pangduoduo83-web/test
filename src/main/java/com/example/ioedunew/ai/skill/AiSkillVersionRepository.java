package com.example.ioedunew.ai.skill;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface AiSkillVersionRepository extends JpaRepository<AiSkillVersion, Long> {
    List<AiSkillVersion> findBySkillIdOrderByVersionDesc(Long skillId);

    void deleteBySkillId(Long skillId);
}
