package com.example.ioeduhub.entity;

import lombok.Data;

import javax.persistence.Column;
import javax.persistence.Entity;
import javax.persistence.GeneratedValue;
import javax.persistence.GenerationType;
import javax.persistence.Id;
import javax.persistence.Table;
import java.time.LocalDateTime;

@Data
@Entity
@Table(name = "hub_review_logs")
public class HubReviewLog {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private Long itemId;

    private Long versionId;

    @Column(nullable = false, length = 50)
    private String reviewer;

    @Column(nullable = false, length = 20)
    private String decision;

    @Column(length = 500)
    private String comment;

    @Column(nullable = false)
    private LocalDateTime reviewedAt = LocalDateTime.now();
}
