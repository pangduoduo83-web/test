package com.example.ioedunew.repository;

import com.example.ioedunew.entity.Submission;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

/** 项目成果提交仓库 */
public interface SubmissionRepository extends JpaRepository<Submission, Long> {

    Optional<Submission> findTopByUserIdAndProjectIdOrderBySubmittedAtDesc(Long userId, Long projectId);

    List<Submission> findByStatusOrderBySubmittedAtDesc(String status);

    List<Submission> findAllByOrderBySubmittedAtDesc();
}
