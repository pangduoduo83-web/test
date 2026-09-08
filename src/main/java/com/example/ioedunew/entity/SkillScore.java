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
 * 技能评分:每个用户每个技能维度一条。
 * score 是综合分(雷达主线、AI 规划、看板都用它):没有实证时等于自评分(或基线 30),
 * 有项目评分实证后按评级方式增量更新;selfScore 只保留学生的自我认知,用于对照。
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

    /** 综合分 0-100 */
    @Column(nullable = false)
    private Integer score = 0;

    /** 学生自评 0-100,未自评为 null */
    private Integer selfScore;

    /** 已计入综合分的实证次数(项目评分 / 教师确认的 AI 证据) */
    @Column(nullable = false)
    private Integer evidenceCount = 0;

    @Column(nullable = false)
    private LocalDateTime updatedAt = LocalDateTime.now();
}
