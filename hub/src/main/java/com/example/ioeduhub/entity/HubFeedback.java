package com.example.ioeduhub.entity;

import lombok.Data;

import javax.persistence.Column;
import javax.persistence.Entity;
import javax.persistence.GeneratedValue;
import javax.persistence.GenerationType;
import javax.persistence.Id;
import javax.persistence.Lob;
import javax.persistence.Table;
import java.time.LocalDateTime;

/** 全平台反馈汇总，平台库单库保存，天然跨客户站点可检索。 */
@Data
@Entity
@Table(name = "hub_feedbacks")
public class HubFeedback {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    @Column(nullable = false, length = 32) private String tenantCode;
    @Column(nullable = false, length = 100) private String tenantName;
    private Long userId;
    @Column(nullable = false, length = 50) private String userName;
    @Column(nullable = false, length = 20) private String userRole;
    @Column(nullable = false, length = 20) private String category;
    @Column(nullable = false, length = 120) private String title;
    @Lob @Column(nullable = false) private String content;
    @Lob private String attachments;
    @Column(length = 300) private String pageUrl;
    @Column(nullable = false, length = 20) private String status = "OPEN";
    @Lob private String adminReply;
    @Column(length = 50) private String adminName;
    @Column(nullable = false) private LocalDateTime createdAt = LocalDateTime.now();
    @Column(nullable = false) private LocalDateTime updatedAt = LocalDateTime.now();
}
