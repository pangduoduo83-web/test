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
 * 课程班(教学班):一位老师带一群学生,可按班布置项目、发公告、看进度。
 * 学生凭加入码自助加入,或由老师 / 管理员按学号、邮箱批量拉入。
 */
@Data
@Entity
@Table(name = "course_classes")
public class CourseClass {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, length = 60)
    private String name;

    @Column(length = 300)
    private String description;

    private Long teacherId;

    @Column(length = 50)
    private String teacherName;

    @Column(nullable = false, unique = true, length = 12)
    private String joinCode;

    @Column(nullable = false)
    private Boolean joinEnabled = true;

    /** ACTIVE / ARCHIVED */
    @Column(nullable = false, length = 20)
    private String status = "ACTIVE";

    private Long createdBy;

    @Column(nullable = false)
    private LocalDateTime createdAt = LocalDateTime.now();

    @Column(nullable = false)
    private LocalDateTime updatedAt = LocalDateTime.now();
}
