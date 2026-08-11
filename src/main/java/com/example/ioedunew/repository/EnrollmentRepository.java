package com.example.ioedunew.repository;

import com.example.ioedunew.entity.Enrollment;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

/** 项目报名仓库 */
public interface EnrollmentRepository extends JpaRepository<Enrollment, Long> {

    Optional<Enrollment> findByUserIdAndProjectId(Long userId, Long projectId);

    List<Enrollment> findByUserIdOrderByEnrolledAtDesc(Long userId);

    List<Enrollment> findByProjectIdOrderByEnrolledAtDesc(Long projectId);

    long countByUserId(Long userId);

    long countByUserIdAndStatus(Long userId, String status);

    boolean existsByUserId(Long userId);
}
