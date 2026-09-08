package com.example.ioedunew.repository;

import com.example.ioedunew.entity.SkillScoreEvent;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;

/** 技能分变动流水仓库 */
public interface SkillScoreEventRepository extends JpaRepository<SkillScoreEvent, Long> {

    List<SkillScoreEvent> findByUserIdOrderByCreatedAtAscIdAsc(Long userId);

    void deleteByUserId(Long userId);

    void deleteBySkillName(String skillName);

    @Modifying
    @Query("update SkillScoreEvent e set e.skillName = :newName where e.skillName = :oldName")
    int renameSkill(@Param("oldName") String oldName, @Param("newName") String newName);
}
