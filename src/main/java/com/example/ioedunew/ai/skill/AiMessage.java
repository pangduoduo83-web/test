package com.example.ioedunew.ai.skill;

import com.fasterxml.jackson.annotation.JsonRawValue;
import lombok.Data;

import javax.persistence.Column;
import javax.persistence.Entity;
import javax.persistence.GeneratedValue;
import javax.persistence.GenerationType;
import javax.persistence.Id;
import javax.persistence.Table;
import java.time.LocalDateTime;

/** 会话消息:role 为 user / assistant / tool;assistant 的工具调用请求存 toolCalls(JSON) */
@Data
@Entity
@Table(name = "ai_messages")
public class AiMessage {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private Long conversationId;

    @Column(nullable = false, length = 20)
    private String role;

    @Column(columnDefinition = "LONGTEXT")
    private String content;

    @JsonRawValue
    @Column(columnDefinition = "TEXT")
    private String toolCalls;

    @Column(length = 80)
    private String toolCallId;

    @Column(length = 64)
    private String toolName;

    @Column(nullable = false)
    private LocalDateTime createdAt = LocalDateTime.now();
}
