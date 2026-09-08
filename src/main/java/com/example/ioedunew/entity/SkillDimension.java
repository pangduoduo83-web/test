package com.example.ioedunew.entity;

import lombok.Data;

import javax.persistence.Column;
import javax.persistence.Entity;
import javax.persistence.GeneratedValue;
import javax.persistence.GenerationType;
import javax.persistence.Id;
import javax.persistence.Table;
import javax.persistence.UniqueConstraint;
import java.time.LocalDateTime;

/**
 * 技能维度:雷达图的一个顶点,由管理员在后台维护。
 * skill_scores.skill_name 与 projects.skill_requirements[].name 都以 name 关联本表。
 */
@Data
@Entity
@Table(name = "skill_dimensions", uniqueConstraints = @UniqueConstraint(columnNames = {"name"}))
public class SkillDimension {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, length = 30)
    private String name;

    @Column(length = 200)
    private String description;

    @Column(nullable = false)
    private Integer sortOrder = 0;

    /** 停用后不再出现在雷达图与测评中,历史分数保留 */
    @Column(nullable = false)
    private Boolean enabled = true;

    @Column(nullable = false)
    private LocalDateTime createdAt = LocalDateTime.now();

    @Column(nullable = false)
    private LocalDateTime updatedAt = LocalDateTime.now();
}
