package com.example.ioedunew.ai.skill;

import lombok.Data;

import javax.persistence.Column;
import javax.persistence.Entity;
import javax.persistence.GeneratedValue;
import javax.persistence.GenerationType;
import javax.persistence.Id;
import javax.persistence.Table;
import java.time.LocalDateTime;

/** 管理员对某个工具的策略覆盖:开关、最低角色、是否需确认、每人每日次数 */
@Data
@Entity
@Table(name = "ai_tool_policies")
public class AiToolPolicy {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, unique = true, length = 64)
    private String toolName;

    @Column(nullable = false)
    private Boolean enabled = true;

    @Column(length = 20)
    private String minRole;

    @Column(nullable = false)
    private Boolean requiresConfirmation = false;

    private Integer dailyLimit;

    @Column(nullable = false)
    private LocalDateTime updatedAt = LocalDateTime.now();
}
