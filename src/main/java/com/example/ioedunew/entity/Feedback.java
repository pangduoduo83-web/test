package com.example.ioedunew.entity;

import lombok.Data;

import javax.persistence.Column;
import javax.persistence.Entity;
import javax.persistence.GeneratedValue;
import javax.persistence.GenerationType;
import javax.persistence.Id;
import javax.persistence.Lob;
import javax.persistence.Table;
import java.time.LocalDateTime;

/**
 * 用户问题反馈。实体位于租户库中，因此天然按站点隔离。
 */
@Data
@Entity
@Table(name = "feedbacks")
public class Feedback {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private Long userId;

    @Column(nullable = false, length = 50)
    private String userName;

    @Column(nullable = false, length = 20)
    private String userRole;

    /** PROCESS / OPERATION / SUGGESTION / OTHER */
    @Column(nullable = false, length = 20)
    private String category;

    @Column(nullable = false, length = 120)
    private String title;

    @Lob
    @Column(nullable = false)
    private String content;

    /** JSON array: [{"url":"/uploads/...","name":"截图.png"}] */
    @Lob
    private String attachments;

    @Column(length = 300)
    private String pageUrl;

    /** OPEN / IN_PROGRESS / RESOLVED / CLOSED */
    @Column(nullable = false, length = 20)
    private String status = "OPEN";

    @Lob
    private String adminReply;

    private Long adminId;

    @Column(length = 50)
    private String adminName;

    @Column(nullable = false)
    private LocalDateTime createdAt = LocalDateTime.now();

    @Column(nullable = false)
    private LocalDateTime updatedAt = LocalDateTime.now();
}
