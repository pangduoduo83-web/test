package com.example.ioedunew.repository;

import com.example.ioedunew.entity.Project;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

/** 项目仓库 */
public interface ProjectRepository extends JpaRepository<Project, Long> {

    List<Project> findByStatus(String status);

    boolean existsByMentorId(Long mentorId);
}
