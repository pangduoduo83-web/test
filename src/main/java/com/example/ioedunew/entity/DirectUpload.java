package com.example.ioedunew.entity;

import lombok.Data;
import javax.persistence.*;
import java.time.LocalDateTime;

@Data
@Entity
@Table(name = "direct_uploads")
public class DirectUpload {
    @Id @Column(length = 32) private String id;
    @Column(nullable = false) private Long userId;
    @Column(nullable = false, length = 16) private String kind;
    @Column(nullable = false, length = 255) private String name;
    @Column(nullable = false, length = 190) private String relativePath;
    @Column(nullable = false, length = 64) private String bucket;
    @Column(nullable = false, length = 512) private String stagingKey;
    @Column(nullable = false) private long sizeBytes;
    @Column(nullable = false) private LocalDateTime expiresAt;
    @Column(nullable = false) private boolean completed;
}
