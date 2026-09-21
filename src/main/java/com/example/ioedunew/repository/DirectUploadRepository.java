package com.example.ioedunew.repository;
import com.example.ioedunew.entity.DirectUpload;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.Optional;
import java.time.LocalDateTime;
public interface DirectUploadRepository extends JpaRepository<DirectUpload, String> {
    Optional<DirectUpload> findByIdAndUserId(String id, Long userId);
    long countByUserIdAndCompletedFalseAndExpiresAtAfter(Long userId, LocalDateTime now);
    long deleteByExpiresAtBefore(LocalDateTime cutoff);
}
