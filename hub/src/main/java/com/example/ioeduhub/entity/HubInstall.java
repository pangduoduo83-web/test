package com.example.ioeduhub.entity;

import lombok.Data;

import javax.persistence.Column;
import javax.persistence.Entity;
import javax.persistence.GeneratedValue;
import javax.persistence.GenerationType;
import javax.persistence.Id;
import javax.persistence.Table;
import java.time.LocalDateTime;

/** 安装记录:哪个客户在何时拉取了哪个版本 */
@Data
@Entity
@Table(name = "hub_installs")
public class HubInstall {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private Long itemId;

    @Column(nullable = false)
    private Long versionId;

    @Column(nullable = false)
    private Long tenantId;

    @Column(length = 50)
    private String installedBy;

    @Column(nullable = false)
    private LocalDateTime installedAt = LocalDateTime.now();
}
