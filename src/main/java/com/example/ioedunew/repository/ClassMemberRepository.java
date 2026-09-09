package com.example.ioedunew.repository;

import com.example.ioedunew.entity.ClassMember;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface ClassMemberRepository extends JpaRepository<ClassMember, Long> {
    List<ClassMember> findByClassIdOrderByJoinedAtAsc(Long classId);

    List<ClassMember> findByUserId(Long userId);

    Optional<ClassMember> findByClassIdAndUserId(Long classId, Long userId);

    long countByClassId(Long classId);

    void deleteByClassId(Long classId);

    void deleteByUserId(Long userId);
}
