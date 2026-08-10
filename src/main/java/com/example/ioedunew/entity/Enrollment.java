package com.example.ioedunew.entity;

import lombok.Data;

import javax.persistence.Column;
import javax.persistence.Entity;
import javax.persistence.GeneratedValue;
import javax.persistence.GenerationType;
import javax.persistence.Id;
import javax.persistence.Table;
import javax.persistence.UniqueConstraint;
import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * 项目报名:学生参与项目的进度记录。
 */
@Data
@Entity
@Table(name = "enrollments", uniqueConstraints = @UniqueConstraint(columnNames = {"userId", "projectId"}))
public class Enrollment {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private Long userId;

    @Column(nullable = false)
    private Long projectId;

    /** 冗余显示字段 */
    @Column(length = 100)
    private String projectTitle;

    /** 0-100 */
    @Column(nullable = false)
    private Integer progress = 0;

    @Column(length = 100)
    private String currentTask;

    /** IN_PROGRESS / COMPLETED */
    @Column(nullable = false, length = 20)
    private String status = "IN_PROGRESS";

    private LocalDate deadline;

    @Column(nullable = false)
    private LocalDateTime enrolledAt = LocalDateTime.now();
}
