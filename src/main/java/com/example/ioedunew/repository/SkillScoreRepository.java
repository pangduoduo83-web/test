package com.example.ioedunew.repository;

import com.example.ioedunew.entity.SkillScore;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

/** 技能评分仓库 */
public interface SkillScoreRepository extends JpaRepository<SkillScore, Long> {

    List<SkillScore> findByUserId(Long userId);

    void deleteByUserId(Long userId);
}
