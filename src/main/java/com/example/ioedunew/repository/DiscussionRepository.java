package com.example.ioedunew.repository;

import com.example.ioedunew.entity.Discussion;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

/** 项目讨论仓库 */
public interface DiscussionRepository extends JpaRepository<Discussion, Long> {

    List<Discussion> findByProjectIdOrderByCreatedAtDesc(Long projectId);
}
