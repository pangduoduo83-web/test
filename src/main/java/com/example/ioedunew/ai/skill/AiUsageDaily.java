package com.example.ioedunew.ai.skill;

import lombok.Data;

import javax.persistence.Column;
import javax.persistence.Entity;
import javax.persistence.GeneratedValue;
import javax.persistence.GenerationType;
import javax.persistence.Id;
import javax.persistence.Table;
import java.time.LocalDate;

/** 每用户每日用量汇总,用于配额与成本统计 */
@Data
@Entity
@Table(name = "ai_usage_daily")
public class AiUsageDaily {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private Long userId;

    @Column(nullable = false)
    private LocalDate day;

    @Column(nullable = false)
    private Integer runs = 0;

    @Column(nullable = false)
    private Integer promptTokens = 0;

    @Column(nullable = false)
    private Integer completionTokens = 0;
}
