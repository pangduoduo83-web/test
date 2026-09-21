package com.example.ioedunew.entity;

import lombok.Data;
import javax.persistence.*;

@Data
@Entity
@Table(name = "submission_assets")
public class SubmissionAsset {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    private Long userId;
    @Column(nullable = false, unique = true, length = 255)
    private String url;
    @Column(nullable = false, length = 255)
    private String name;
    private Long size;
}
