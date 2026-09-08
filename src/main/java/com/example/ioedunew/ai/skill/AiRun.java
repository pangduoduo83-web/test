package com.example.ioedunew.ai.skill;

import lombok.Data;

import javax.persistence.Column;
import javax.persistence.Entity;
import javax.persistence.GeneratedValue;
import javax.persistence.GenerationType;
import javax.persistence.Id;
import javax.persistence.Table;
import java.time.LocalDateTime;

/** 一次 SKILL 运行的审计记录 */
@Data
@Entity
@Table(name = "ai_runs")
public class AiRun {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private Long userId;

    @Column(length = 50)
    private String userName;

    @Column(length = 60)
    private String skillKey;

    private Integer skillVersion;

    private Long conversationId;

    /** SUCCESS / FAILED / CONFIRM_REQUIRED */
    @Column(nullable = false, length = 20)
    private String status;

    @Column(columnDefinition = "TEXT")
    private String input;

    @Column(columnDefinition = "LONGTEXT")
    private String output;

    @Column(nullable = false)
    private Integer toolRounds = 0;

    @Column(nullable = false)
    private Integer promptTokens = 0;

    @Column(nullable = false)
    private Integer completionTokens = 0;

    @Column(nullable = false)
    private Integer latencyMs = 0;

    @Column(length = 500)
    private String error;

    @Column(nullable = false)
    private LocalDateTime createdAt = LocalDateTime.now();
}
