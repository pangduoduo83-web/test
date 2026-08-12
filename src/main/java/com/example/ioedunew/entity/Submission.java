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
 * 项目成果提交:学生对已报名项目交付成果,由管理员/导师评分。
 * 状态:SUBMITTED 待评审 → GRADED 已评分。
 */
@Data
@Entity
@Table(name = "submissions")
public class Submission {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private Long userId;

    @Column(nullable = false)
    private Long projectId;

    /** 冗余显示字段 */
    @Column(length = 50)
    private String userName;

    @Column(length = 100)
    private String projectTitle;

    @Column(nullable = false, length = 2000)
    private String content;

    /** 成果截图/附件图片地址 */
    @Column(length = 255)
    private String attachmentUrl;

    /** 对应的考核项名称;为空表示整体单一成果(兼容旧数据) */
    @Column(length = 50)
    private String assessmentName;

    /** SUBMITTED / GRADED */
    @Column(nullable = false, length = 20)
    private String status = "SUBMITTED";

    /** 0-100,评分后写入 */
    private Integer score;

    @Column(length = 500)
    private String feedback;

    @Column(length = 50)
    private String graderName;

    @Column(nullable = false)
    private LocalDateTime submittedAt = LocalDateTime.now();

    private LocalDateTime gradedAt;
}
