package com.example.ioedunew.repository;
import com.example.ioedunew.entity.SubmissionAsset;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.Optional;
public interface SubmissionAssetRepository extends JpaRepository<SubmissionAsset, Long> {
    Optional<SubmissionAsset> findByUrlAndUserId(String url, Long userId);
}
