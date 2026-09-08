package com.example.ioeduhub.repository;

import com.example.ioeduhub.entity.HubInstall;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface InstallRepo extends JpaRepository<HubInstall, Long> {
    List<HubInstall> findByTenantIdOrderByInstalledAtDesc(Long tenantId);

    List<HubInstall> findByItemIdOrderByInstalledAtDesc(Long itemId);

    long count();
}
