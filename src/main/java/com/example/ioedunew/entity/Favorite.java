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
 * 项目收藏。
 */
@Data
@Entity
@Table(name = "favorites", uniqueConstraints = @UniqueConstraint(columnNames = {"userId", "projectId"}))
public class Favorite {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private Long userId;

    @Column(nullable = false)
    private Long projectId;

    @Column(nullable = false)
    private LocalDateTime createdAt = LocalDateTime.now();
}
