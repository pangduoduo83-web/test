package com.example.ioedunew.entity;

import lombok.Data;

import javax.persistence.Column;
import javax.persistence.Entity;
import javax.persistence.GeneratedValue;
import javax.persistence.GenerationType;
import javax.persistence.Id;
import javax.persistence.Index;
import javax.persistence.Table;
import java.time.LocalDateTime;

/**
 * 技能分变动流水:每次综合分被改写都记一条,成长曲线与"画像变动记录"都由它驱动。
 */
@Data
@Entity
@Table(name = "skill_score_events",
        indexes = @Index(name = "idx_skill_score_events_user_time", columnList = "userId, createdAt"))
public class SkillScoreEvent {

    public static final String SOURCE_INIT = "INIT";
    public static final String SOURCE_SELF = "SELF";
    public static final String SOURCE_PROJECT = "PROJECT";
    /** AI 出题的客观测评 */
    public static final String SOURCE_QUIZ = "QUIZ";

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private Long userId;

    @Column(nullable = false, length = 30)
    private String skillName;

    /** INIT 注册基线 / SELF 自评 / PROJECT 项目评分实证(含教师确认的 AI 证据) */
    @Column(nullable = false, length = 20)
    private String source;

    @Column(nullable = false)
    private Integer beforeScore;

    @Column(nullable = false)
    private Integer afterScore;

    /** 本次变动后的综合评分快照,成长曲线直接取用 */
    @Column(nullable = false)
    private Integer overallAfter;

    /** 关联对象 id:PROJECT 为成果提交 id */
    private Long refId;

    @Column(length = 200)
    private String note;

    @Column(nullable = false)
    private LocalDateTime createdAt = LocalDateTime.now();
}
