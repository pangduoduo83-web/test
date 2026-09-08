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

/**
 * SKILL:一段可复用的 AI 能力定义 = 提示词 + 输入结构(JSON Schema)+ 工具白名单 + 模型参数,全部在 spec JSON 里。
 * scope:TENANT 本站共享(教师/管理员创建)/ PERSONAL 个人私有;内置 SKILL 来自 classpath 不入库(scope=BUILTIN)。
 * 同一份定义既能被用户直接对话调用,也能被 Agent 当作工具调用(见 SkillRunner)。
 */
@Data
@Entity
@Table(name = "ai_skills")
public class AiSkill {

    public static final String SCOPE_BUILTIN = "BUILTIN";
    public static final String SCOPE_TENANT = "TENANT";
    public static final String SCOPE_PERSONAL = "PERSONAL";

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, unique = true, length = 60)
    private String skillKey;

    @Column(nullable = false, length = 60)
    private String name;

    @Column(length = 300)
    private String description;

    @Column(length = 10)
    private String icon;

    @Column(length = 30)
    private String category;

    @Column(nullable = false, length = 20)
    private String scope = SCOPE_PERSONAL;

    private Long ownerUserId;

    /** ACTIVE / DISABLED */
    @Column(nullable = false, length = 20)
    private String status = "ACTIVE";

    @JsonRawValue
    @Column(nullable = false, columnDefinition = "LONGTEXT")
    private String spec;

    @Column(nullable = false)
    private Integer version = 1;

    /** 由哪个 SKILL 另存而来(内置或他人的 key) */
    @Column(length = 60)
    private String forkedFrom;

    private Long hubItemId;

    private Integer hubVersionNo;

    private Long createdBy;

    @Column(nullable = false)
    private LocalDateTime createdAt = LocalDateTime.now();

    @Column(nullable = false)
    private LocalDateTime updatedAt = LocalDateTime.now();
}
