package com.example.ioeduhub.repository;

import com.example.ioeduhub.entity.HubTenant;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface TenantRepo extends JpaRepository<HubTenant, Long> {
    Optional<HubTenant> findByApiKeyHash(String apiKeyHash);

    Optional<HubTenant> findByCode(String code);
}
