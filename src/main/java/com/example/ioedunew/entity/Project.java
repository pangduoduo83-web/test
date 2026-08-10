package com.example.ioedunew.entity;

import com.example.ioedunew.common.RawJsonStringDeserializer;
import com.fasterxml.jackson.annotation.JsonRawValue;
import com.fasterxml.jackson.databind.annotation.JsonDeserialize;
import lombok.Data;

import javax.persistence.Column;
import javax.persistence.Entity;
import javax.persistence.GeneratedValue;
import javax.persistence.GenerationType;
import javax.persistence.Id;
import javax.persistence.Table;
import java.time.LocalDateTime;

/**
 * 开源硬件项目:项目中心条目,含教学大纲、BOM、技能要求等 JSON 结构化内容。
 */
@Data
@Entity
@Table(name = "projects")
public class Project {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, length = 100)
    private String title;

    @Column(length = 300)
    private String summary;

    @Column(columnDefinition = "TEXT")
    private String description;

    /** 入门 / 进阶 / 挑战 */
    @Column(nullable = false, length = 10)
    private String difficulty = "入门";

    @Column(length = 20)
    private String duration = "2周";

    @Column(length = 20)
    private String teamSize = "1人";

    @Column(length = 30)
    private String category;

    @Column(length = 10)
    private String icon = "🔌";

    /** 项目封面图地址,如 /uploads/202607/xxx.png */
    @Column(length = 255)
    private String coverUrl;

    @Column(length = 50)
    private String mentor;

    /** 授课讲师的用户 ID(教师端凭此管理自己的项目) */
    private Long mentorId;

    @Column(length = 50)
    private String author;

    @Column(length = 30)
    private String license = "GPL-3.0";

    @Column(nullable = false)
    private Boolean verified = false;

    /** PCB 层数 */
    private Integer layers;

    /** PCB 尺寸,如 45x30mm */
    @Column(length = 30)
    private String pcbSize;

    /** 预估成本(元) */
    private Double cost;

    @Column(nullable = false)
    private Double rating = 5.0;

    @Column(nullable = false)
    private Integer enrolledCount = 0;

    @Column(nullable = false)
    private Integer completionRate = 0;

    @Column(nullable = false)
    private Integer views = 0;

    @Column(nullable = false)
    private Integer favoriteCount = 0;

    @Column(nullable = false)
    private Integer downloads = 0;

    /** Fork 数(参考站卡片与详情页的统计项) */
    private Integer forks = 0;

    /** 标签,JSON 数组 */
    @JsonRawValue
    @JsonDeserialize(using = RawJsonStringDeserializer.class)
    @Column(columnDefinition = "TEXT")
    private String tags = "[]";

    /** 项目特性,JSON 数组 */
    @JsonRawValue
    @JsonDeserialize(using = RawJsonStringDeserializer.class)
    @Column(columnDefinition = "TEXT")
    private String features = "[]";

    /** 学习目标,JSON 数组 */
    @JsonRawValue
    @JsonDeserialize(using = RawJsonStringDeserializer.class)
    @Column(columnDefinition = "TEXT")
    private String learningGoals = "[]";

    /** 前置知识要求,JSON 数组 */
    @JsonRawValue
    @JsonDeserialize(using = RawJsonStringDeserializer.class)
    @Column(columnDefinition = "TEXT")
    private String prerequisites = "[]";

    /** 技能要求,JSON 数组 [{name, required}] */
    @JsonRawValue
    @JsonDeserialize(using = RawJsonStringDeserializer.class)
    @Column(columnDefinition = "TEXT")
    private String skillRequirements = "[]";

    /** 教学大纲,JSON 数组 [{phase, title, content, hours}] */
    @JsonRawValue
    @JsonDeserialize(using = RawJsonStringDeserializer.class)
    @Column(columnDefinition = "TEXT")
    private String syllabus = "[]";

    /** BOM 清单,JSON 数组 [{ref, name, qty, footprint, price}] */
    @JsonRawValue
    @JsonDeserialize(using = RawJsonStringDeserializer.class)
    @Column(columnDefinition = "TEXT")
    private String bom = "[]";

    /** 学习资源,JSON 数组 [{type, name}] */
    @JsonRawValue
    @JsonDeserialize(using = RawJsonStringDeserializer.class)
    @Column(columnDefinition = "TEXT")
    private String resources = "[]";

    /** 所需设备名称,JSON 数组 */
    @JsonRawValue
    @JsonDeserialize(using = RawJsonStringDeserializer.class)
    @Column(columnDefinition = "TEXT")
    private String equipmentNames = "[]";

    /** PUBLISHED / DRAFT */
    @Column(nullable = false, length = 20)
    private String status = "PUBLISHED";

    @Column(nullable = false)
    private LocalDateTime createdAt = LocalDateTime.now();

    @Column(nullable = false)
    private LocalDateTime updatedAt = LocalDateTime.now();
}
