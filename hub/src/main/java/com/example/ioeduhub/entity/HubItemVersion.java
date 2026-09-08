package com.example.ioeduhub.entity;

import com.fasterxml.jackson.annotation.JsonIgnore;
import lombok.Data;

import javax.persistence.Column;
import javax.persistence.Entity;
import javax.persistence.GeneratedValue;
import javax.persistence.GenerationType;
import javax.persistence.Id;
import javax.persistence.Table;
import java.time.LocalDateTime;

/** 条目版本:payload 为发布方导出的项目全量 JSON,生成后不可变 */
@Data
@Entity
@Table(name = "hub_item_versions")
public class HubItemVersion {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private Long itemId;

    @Column(nullable = false)
    private Integer versionNo;

    @JsonIgnore
    @Column(nullable = false, columnDefinition = "LONGTEXT")
    private String payload;

    @Column(length = 500)
    private String changelog;

    @Column(length = 50)
    private String createdBy;

    @Column(nullable = false)
    private LocalDateTime createdAt = LocalDateTime.now();
}
