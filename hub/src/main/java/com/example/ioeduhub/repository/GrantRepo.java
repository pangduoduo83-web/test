package com.example.ioeduhub.repository;

import com.example.ioeduhub.entity.HubItemGrant;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface GrantRepo extends JpaRepository<HubItemGrant, Long> {
    List<HubItemGrant> findByItemId(Long itemId);

    boolean existsByItemIdAndTenantId(Long itemId, Long tenantId);

    void deleteByItemId(Long itemId);
}
