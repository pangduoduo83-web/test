package com.example.ioedunew.entity;

import com.fasterxml.jackson.annotation.JsonIgnore;
import lombok.Data;

import javax.persistence.Column;
import javax.persistence.Entity;
import javax.persistence.GeneratedValue;
import javax.persistence.GenerationType;
import javax.persistence.Id;
import javax.persistence.Table;
import java.time.LocalDateTime;

/**
 * 用户:学生、教师与管理员共用一张表,以 role 区分。
 */
@Data
@Entity
@Table(name = "users")
public class User {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, length = 50)
    private String name;

    @Column(nullable = false, unique = true, length = 100)
    private String email;

    /** BCrypt 哈希,任何接口不得返回 */
    @JsonIgnore
    @Column(nullable = false)
    private String passwordHash;

    @Column(length = 30)
    private String studentNo;

    @Column(length = 50)
    private String major;

    @Column(length = 20)
    private String grade;

    /** STUDENT / TEACHER / ADMIN */
    @Column(nullable = false, length = 20)
    private String role = "STUDENT";

    /** 头像地址,如 /uploads/202607/xxx.png */
    @Column(length = 255)
    private String avatarUrl;

    /** 实践经验值,等级 = exp / 100 + 1 */
    @Column(nullable = false)
    private Integer exp = 0;

    /** 本周实践时长(小时) */
    @Column(nullable = false)
    private Integer weeklyHours = 0;

    @Column(nullable = false)
    private Boolean enabled = true;

    @Column(nullable = false)
    private LocalDateTime createdAt = LocalDateTime.now();
}
