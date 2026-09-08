package com.example.ioeduhub.entity;

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
 * 商店条目(目前 type=PROJECT,预留 SKILL 等)。
 * 可见范围 visibility:PUBLIC 所有客户可见;RESTRICTED 仅被定向分享(hub_item_grants)的客户可见。
 * 审核状态 review_status:PENDING 待审 / APPROVED 已上架 / REJECTED 已驳回 / OFFLINE 已下架。
 */
@Data
@Entity
@Table(name = "hub_items")
public class HubItem {

    public static final String TYPE_PROJECT = "PROJECT";
    public static final String VIS_PUBLIC = "PUBLIC";
    public static final String VIS_RESTRICTED = "RESTRICTED";
    public static final String ST_PENDING = "PENDING";
    public static final String ST_APPROVED = "APPROVED";
    public static final String ST_REJECTED = "REJECTED";
    public static final String ST_OFFLINE = "OFFLINE";

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, length = 20)
    private String type = TYPE_PROJECT;

    @Column(nullable = false, length = 100)
    private String title;

    @Column(length = 300)
    private String summary;

    @Column(length = 30)
    private String category;

    /** JSON 数组字符串 */
    @JsonRawValue
    @Column(columnDefinition = "TEXT")
    private String tags = "[]";

    /** 商店内的封面地址 /hub-assets/{sha}.{ext} */
    @Column(length = 255)
    private String coverUrl;

    @Column(nullable = false)
    private Long publisherTenantId;

    @Column(length = 100)
    private String publisherTenantName;

    @Column(length = 50)
    private String publisherUserName;

    @Column(nullable = false, length = 20)
    private String visibility = VIS_PUBLIC;

    @Column(nullable = false, length = 20)
    private String reviewStatus = ST_PENDING;

    @Column(length = 500)
    private String reviewComment;

    /** 最近一次审核通过的版本;为空表示尚未上架过 */
    private Long currentVersionId;

    @Column(nullable = false)
    private Integer latestVersionNo = 1;

    @Column(nullable = false)
    private Integer installCount = 0;

    /** 平台推荐置顶:在各客户商店里排在最前并带推荐标记 */
    @Column(nullable = false)
    private Boolean featured = false;

    private LocalDateTime featuredAt;

    @Column(nullable = false)
    private LocalDateTime createdAt = LocalDateTime.now();

    @Column(nullable = false)
    private LocalDateTime updatedAt = LocalDateTime.now();
}
