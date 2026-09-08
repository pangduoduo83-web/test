package com.example.ioedunew.repository;

import com.example.ioedunew.entity.SkillScore;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

/** 技能评分仓库 */
public interface SkillScoreRepository extends JpaRepository<SkillScore, Long> {

    List<SkillScore> findByUserId(Long userId);

    Optional<SkillScore> findByUserIdAndSkillName(Long userId, String skillName);

    long countBySkillName(String skillName);

    void deleteByUserId(Long userId);

    void deleteBySkillName(String skillName);

    @Modifying
    @Query("update SkillScore s set s.skillName = :newName where s.skillName = :oldName")
    int renameSkill(@Param("oldName") String oldName, @Param("newName") String newName);
}
