package com.example.ioedunew.entity;

import lombok.Data;
import javax.persistence.*;
import java.time.LocalDateTime;

@Data
@Entity
@Table(name = "review_jobs")
public class ReviewJob {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    private Long submissionId;
    private String fingerprint;
    private String status;
    private int progress;
    private String message;
    @Column(columnDefinition = "LONGTEXT") private String materials;
    @Column(columnDefinition = "LONGTEXT") private String result;
    private LocalDateTime createdAt = LocalDateTime.now();
    private LocalDateTime updatedAt = LocalDateTime.now();
}
