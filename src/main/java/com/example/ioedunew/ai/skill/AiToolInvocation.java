package com.example.ioedunew.ai.skill;

import lombok.Data;

import javax.persistence.Column;
import javax.persistence.Entity;
import javax.persistence.GeneratedValue;
import javax.persistence.GenerationType;
import javax.persistence.Id;
import javax.persistence.Table;
import java.time.LocalDateTime;

/** 工具调用审计:参数级记录 */
@Data
@Entity
@Table(name = "ai_tool_invocations")
public class AiToolInvocation {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private Long runId;

    @Column(nullable = false)
    private Long userId;

    @Column(nullable = false, length = 64)
    private String toolName;

    @Column(columnDefinition = "TEXT")
    private String arguments;

    @Column(columnDefinition = "TEXT")
    private String resultSummary;

    @Column(nullable = false)
    private Boolean ok = true;

    @Column(nullable = false)
    private Boolean confirmed = false;

    @Column(nullable = false)
    private Integer latencyMs = 0;

    @Column(nullable = false)
    private LocalDateTime createdAt = LocalDateTime.now();
}
