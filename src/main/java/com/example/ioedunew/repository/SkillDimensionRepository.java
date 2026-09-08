package com.example.ioedunew.repository;

import com.example.ioedunew.entity.SkillDimension;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

/** 技能维度仓库 */
public interface SkillDimensionRepository extends JpaRepository<SkillDimension, Long> {

    List<SkillDimension> findAllByOrderBySortOrderAscIdAsc();

    List<SkillDimension> findByEnabledTrueOrderBySortOrderAscIdAsc();

    Optional<SkillDimension> findByName(String name);

    boolean existsByName(String name);
}
