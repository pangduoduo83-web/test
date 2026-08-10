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
 * 实验设备:设备图书馆的可借阅条目。
 * specs / tags / docs / suitableProjects 存 JSON 文本,序列化时按原始 JSON 输出。
 */
@Data
@Entity
@Table(name = "equipment")
public class Equipment {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, length = 100)
    private String name;

    @Column(length = 100)
    private String model;

    @Column(length = 500)
    private String description;

    /** 开发板 / 仪器仪表 / 模块 / 工具 */
    @Column(length = 30)
    private String category;

    @Column(length = 50)
    private String location;

    /** AVAILABLE 可借阅 / MAINTENANCE 维护中(已借完由 availableCount=0 推导) */
    @Column(nullable = false, length = 20)
    private String status = "AVAILABLE";

    @Column(nullable = false)
    private Integer totalCount = 1;

    @Column(nullable = false)
    private Integer availableCount = 1;

    @Column(nullable = false)
    private Double rating = 5.0;

    @Column(nullable = false)
    private Integer borrowCount = 0;

    /** 参考价值(元) */
    private Double price;

    @Column(length = 50)
    private String manufacturer;

    /** 卡片展示用 emoji 图标(无图片时的兜底) */
    @Column(length = 10)
    private String icon = "🔧";

    /** 设备图片地址,如 /uploads/202607/xxx.png */
    @Column(length = 255)
    private String imageUrl;

    /** 技术规格,JSON 数组字符串 */
    @JsonRawValue
    @JsonDeserialize(using = RawJsonStringDeserializer.class)
    @Column(columnDefinition = "TEXT")
    private String specs = "[]";

    /** 标签,JSON 数组字符串 */
    @JsonRawValue
    @JsonDeserialize(using = RawJsonStringDeserializer.class)
    @Column(columnDefinition = "TEXT")
    private String tags = "[]";

    /** 参考文档,JSON 数组字符串 */
    @JsonRawValue
    @JsonDeserialize(using = RawJsonStringDeserializer.class)
    @Column(columnDefinition = "TEXT")
    private String docs = "[]";

    /** 适用项目名称,JSON 数组字符串 */
    @JsonRawValue
    @JsonDeserialize(using = RawJsonStringDeserializer.class)
    @Column(columnDefinition = "TEXT")
    private String suitableProjects = "[]";

    @Column(nullable = false)
    private LocalDateTime createdAt = LocalDateTime.now();
}
