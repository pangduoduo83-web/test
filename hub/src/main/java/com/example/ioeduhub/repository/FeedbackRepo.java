package com.example.ioeduhub.repository;

import com.example.ioeduhub.entity.HubFeedback;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface FeedbackRepo extends JpaRepository<HubFeedback, Long> {
    List<HubFeedback> findAllByOrderByCreatedAtDesc();
    List<HubFeedback> findByTenantCodeOrderByCreatedAtDesc(String tenantCode);
}
