package com.example.ioedunew.entity;

import lombok.Data;

import javax.persistence.Column;
import javax.persistence.Entity;
import javax.persistence.GeneratedValue;
import javax.persistence.GenerationType;
import javax.persistence.Id;
import javax.persistence.Table;
import java.time.LocalDateTime;

/**
 * 学习活动日志:学生在平台上的每一次有意义的动作(报名、推进进度、提交成果、被评分、讨论、借阅、AI 对话、自评),
 * 是个人中心"学习活跃度"趋势与"本周学习"统计的唯一数据来源。
 */
@Data
@Entity
@Table(name = "learning_activities")
public class LearningActivity {

    public static final String ENROLL = "ENROLL";
    public static final String PROGRESS = "PROGRESS";
    public static final String SUBMIT = "SUBMIT";
    public static final String GRADED = "GRADED";
    public static final String DISCUSS = "DISCUSS";
    public static final String BORROW = "BORROW";
    public static final String AI_CHAT = "AI_CHAT";
    public static final String SELF_ASSESS = "SELF_ASSESS";

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private Long userId;

    @Column(nullable = false, length = 20)
    private String type;

    private Long refId;

    @Column(length = 200)
    private String title;

    @Column(nullable = false)
    private LocalDateTime createdAt = LocalDateTime.now();
}
