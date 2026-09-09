package com.example.ioedunew.repository;

import com.example.ioedunew.entity.ClassAnnouncement;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface ClassAnnouncementRepository extends JpaRepository<ClassAnnouncement, Long> {
    List<ClassAnnouncement> findTop20ByClassIdOrderByCreatedAtDesc(Long classId);

    void deleteByClassId(Long classId);
}
