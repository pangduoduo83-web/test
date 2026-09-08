package com.example.ioeduhub.repository;

import com.example.ioeduhub.entity.HubAsset;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface AssetRepo extends JpaRepository<HubAsset, Long> {
    Optional<HubAsset> findBySha256(String sha256);
}
