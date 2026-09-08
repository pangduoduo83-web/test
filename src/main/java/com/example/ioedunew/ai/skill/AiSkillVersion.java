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

/** SKILL 每次保存的快照,运行记录引用版本号 */
@Data
@Entity
@Table(name = "ai_skill_versions")
public class AiSkillVersion {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private Long skillId;

    @Column(nullable = false)
    private Integer version;

    @JsonRawValue
    @Column(nullable = false, columnDefinition = "LONGTEXT")
    private String spec;

    @Column(length = 300)
    private String changelog;

    private Long createdBy;

    @Column(nullable = false)
    private LocalDateTime createdAt = LocalDateTime.now();
}
