package com.example.ioedunew.entity;

import lombok.Data;

import javax.persistence.Column;
import javax.persistence.Entity;
import javax.persistence.GeneratedValue;
import javax.persistence.GenerationType;
import javax.persistence.Id;
import javax.persistence.Table;
import java.time.LocalDateTime;

/** 管理端 / 教师端 / 平台接口的写操作审计(谁、何时、对什么接口做了什么) */
@Data
@Entity
@Table(name = "audit_logs")
public class AuditLog {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private Long actorId;

    @Column(length = 50)
    private String actorName;

    @Column(length = 20)
    private String actorRole;

    @Column(nullable = false, length = 8)
    private String method;

    @Column(nullable = false, length = 200)
    private String path;

    @Column(length = 300)
    private String summary;

    @Column(nullable = false)
    private Integer status;

    @Column(length = 45)
    private String ip;

    @Column(nullable = false)
    private LocalDateTime createdAt = LocalDateTime.now();
}
