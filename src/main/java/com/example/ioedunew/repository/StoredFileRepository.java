package com.example.ioedunew.repository;

import com.example.ioedunew.entity.StoredFile;
import org.springframework.data.jpa.repository.JpaRepository;

public interface StoredFileRepository extends JpaRepository<StoredFile, String> { }
