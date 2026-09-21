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

/** 班级教师关系:OWNER 为班级负责人, TEACHER 为协作教师。 */
@Data
@Entity
@Table(name = "class_teachers", uniqueConstraints = @UniqueConstraint(columnNames = {"classId", "teacherId"}))
public class ClassTeacher {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private Long classId;

    @Column(nullable = false)
    private Long teacherId;

    @Column(nullable = false, length = 20)
    private String role = "TEACHER";

    private Long addedBy;

    @Column(nullable = false)
    private LocalDateTime createdAt = LocalDateTime.now();
}
