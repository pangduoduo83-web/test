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

/** 班级作业:给班里所有学生布置某个项目,附统一截止日期 */
@Data
@Entity
@Table(name = "class_assignments", uniqueConstraints = @UniqueConstraint(columnNames = {"classId", "projectId"}))
public class ClassAssignment {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private Long classId;

    @Column(nullable = false)
    private Long projectId;

    @Column(length = 100)
    private String projectTitle;

    private LocalDate deadline;

    @Column(length = 300)
    private String note;

    @Column(nullable = false)
    private LocalDateTime createdAt = LocalDateTime.now();
}
