package com.example.ioedunew.repository;

import com.example.ioedunew.entity.Submission;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

/** 项目成果提交仓库 */
public interface SubmissionRepository extends JpaRepository<Submission, Long> {

    @org.springframework.data.jpa.repository.Lock(javax.persistence.LockModeType.PESSIMISTIC_WRITE)
    @org.springframework.data.jpa.repository.Query("select s from Submission s where s.id = :id")
    Optional<Submission> lockById(@org.springframework.data.repository.query.Param("id") Long id);

    Optional<Submission> findTopByUserIdAndProjectIdOrderBySubmittedAtDesc(Long userId, Long projectId);

    List<Submission> findByUserIdAndProjectIdOrderBySubmittedAtDesc(Long userId, Long projectId);

    List<Submission> findByStatusOrderBySubmittedAtDesc(String status);

    List<Submission> findAllByOrderBySubmittedAtDesc();

    boolean existsByUserId(Long userId);

    boolean existsByUserIdAndProjectId(Long userId, Long projectId);

    void deleteByUserId(Long userId);
}
