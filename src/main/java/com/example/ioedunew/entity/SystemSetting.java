package com.example.ioedunew.entity;

import lombok.Data;

import javax.persistence.Column;
import javax.persistence.Entity;
import javax.persistence.Id;
import javax.persistence.Table;
import java.time.LocalDateTime;

/**
 * 系统设置键值表:目前用于 AI 模型配置(管理后台可改,优先级高于环境变量)。
 */
@Data
@Entity
@Table(name = "system_settings")
public class SystemSetting {

    @Id
    @Column(length = 60)
    private String settingKey;

    @Column(columnDefinition = "TEXT")
    private String settingValue;

    @Column(nullable = false)
    private LocalDateTime updatedAt = LocalDateTime.now();
}
