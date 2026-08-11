package com.example.ioedunew.repository;

import com.example.ioedunew.entity.BorrowRequest;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

/** 借阅申请仓库 */
public interface BorrowRequestRepository extends JpaRepository<BorrowRequest, Long> {

    List<BorrowRequest> findByUserIdOrderByAppliedAtDesc(Long userId);

    List<BorrowRequest> findByStatusOrderByAppliedAtDesc(String status);

    List<BorrowRequest> findAllByOrderByAppliedAtDesc();

    long countByUserId(Long userId);

    long countByUserIdAndStatus(Long userId, String status);

    long countByStatus(String status);

    boolean existsByUserId(Long userId);
}
