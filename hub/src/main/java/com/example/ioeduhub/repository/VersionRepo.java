package com.example.ioeduhub.repository;

import com.example.ioeduhub.entity.HubItemVersion;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface VersionRepo extends JpaRepository<HubItemVersion, Long> {
    List<HubItemVersion> findByItemIdOrderByVersionNoDesc(Long itemId);
}
