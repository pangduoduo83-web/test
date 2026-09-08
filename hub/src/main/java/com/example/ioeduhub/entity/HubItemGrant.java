package com.example.ioeduhub.entity;

import lombok.Data;

import javax.persistence.Column;
import javax.persistence.Entity;
import javax.persistence.GeneratedValue;
import javax.persistence.GenerationType;
import javax.persistence.Id;
import javax.persistence.Table;
import java.time.LocalDateTime;

/** 定向分享:平台管理员把条目指定给某个客户可见 */
@Data
@Entity
@Table(name = "hub_item_grants")
public class HubItemGrant {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private Long itemId;

    @Column(nullable = false)
    private Long tenantId;

    @Column(length = 50)
    private String grantedBy;

    @Column(nullable = false)
    private LocalDateTime grantedAt = LocalDateTime.now();
}
