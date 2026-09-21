package com.example.ioedunew.entity;

import lombok.Data;
import javax.persistence.*;
import java.time.LocalDateTime;

/** Durable OSS location and quota ledger, stored in the current tenant database. */
@Data
@Entity
@Table(name = "stored_files")
public class StoredFile {
    @Id @Column(length = 190) private String relativePath;
    @Column(nullable = false, length = 512) private String objectKey;
    @Column(nullable = false, length = 64) private String bucket;
    @Column(nullable = false) private long sizeBytes;
    @Column(length = 64) private String sha256;
    @Column(nullable = false) private LocalDateTime createdAt = LocalDateTime.now();
}
