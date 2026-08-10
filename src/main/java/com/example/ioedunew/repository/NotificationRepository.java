package com.example.ioedunew.repository;

import com.example.ioedunew.entity.Notification;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

/** 通知仓库 */
public interface NotificationRepository extends JpaRepository<Notification, Long> {

    List<Notification> findTop20ByUserIdOrderByCreatedAtDesc(Long userId);

    long countByUserIdAndIsReadFalse(Long userId);
}
