package com.example.ioedunew.service;

import com.example.ioedunew.entity.StoredFile;
import com.example.ioedunew.repository.StoredFileRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.*;

@Service
public class StoredFileCatalog {
    private final StoredFileRepository repository;
    public StoredFileCatalog(StoredFileRepository repository) { this.repository = repository; }
    // File creation is durable even if a later project import transaction rolls back.
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void record(StoredFile file) { repository.saveAndFlush(file); }
}
