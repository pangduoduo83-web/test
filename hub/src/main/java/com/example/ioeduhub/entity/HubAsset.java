package com.example.ioeduhub.entity;

import lombok.Data;

import javax.persistence.Column;
import javax.persistence.Entity;
import javax.persistence.GeneratedValue;
import javax.persistence.GenerationType;
import javax.persistence.Id;
import javax.persistence.Table;
import java.time.LocalDateTime;

/** 内容寻址的附件(封面、富文本图片、教学资料),按 sha256 去重 */
@Data
@Entity
@Table(name = "hub_assets")
public class HubAsset {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, unique = true, length = 64)
    private String sha256;

    @Column(nullable = false, length = 10)
    private String ext;

    @Column(nullable = false)
    private Long size;

    @Column(length = 100)
    private String mime;

    private Long uploadedByTenantId;

    @Column(nullable = false)
    private LocalDateTime createdAt = LocalDateTime.now();

    public String url() {
        return "/hub-assets/" + sha256 + "." + ext;
    }
}
