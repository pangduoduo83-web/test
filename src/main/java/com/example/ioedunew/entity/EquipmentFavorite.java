package com.example.ioedunew.entity;

import lombok.Data;

import javax.persistence.Column;
import javax.persistence.Entity;
import javax.persistence.GeneratedValue;
import javax.persistence.GenerationType;
import javax.persistence.Id;
import javax.persistence.Table;
import javax.persistence.UniqueConstraint;
import java.time.LocalDateTime;

/**
 * 设备心愿单(设备收藏):替代原网页端仅存 localStorage 的方案,跨端同步。
 */
@Data
@Entity
@Table(name = "equipment_favorites", uniqueConstraints = @UniqueConstraint(columnNames = {"userId", "equipmentId"}))
public class EquipmentFavorite {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private Long userId;

    @Column(nullable = false)
    private Long equipmentId;

    @Column(nullable = false)
    private LocalDateTime createdAt = LocalDateTime.now();
}
