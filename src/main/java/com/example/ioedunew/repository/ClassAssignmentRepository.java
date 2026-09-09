package com.example.ioedunew.repository;

import com.example.ioedunew.entity.ClassAssignment;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface ClassAssignmentRepository extends JpaRepository<ClassAssignment, Long> {
    List<ClassAssignment> findByClassIdOrderByCreatedAtDesc(Long classId);

    Optional<ClassAssignment> findByClassIdAndProjectId(Long classId, Long projectId);

    long countByClassId(Long classId);

    void deleteByClassId(Long classId);
}
