package com.example.ioeduhub.repository;

import com.example.ioeduhub.entity.HubAdmin;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface AdminRepo extends JpaRepository<HubAdmin, Long> {
    Optional<HubAdmin> findByUsername(String username);
}
