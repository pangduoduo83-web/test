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
 * 技能评分:每个用户每个技能维度一条,0-100。
 */
@Data
@Entity
@Table(name = "skill_scores", uniqueConstraints = @UniqueConstraint(columnNames = {"userId", "skillName"}))
public class SkillScore {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private Long userId;

    @Column(nullable = false, length = 30)
    private String skillName;

    @Column(nullable = false)
    private Integer score = 0;

    @Column(nullable = false)
    private LocalDateTime updatedAt = LocalDateTime.now();
}
