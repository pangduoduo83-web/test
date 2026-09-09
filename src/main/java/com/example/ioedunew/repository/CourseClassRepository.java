package com.example.ioedunew.repository;

import com.example.ioedunew.entity.CourseClass;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface CourseClassRepository extends JpaRepository<CourseClass, Long> {
    List<CourseClass> findAllByOrderByUpdatedAtDesc();

    List<CourseClass> findByTeacherIdOrderByUpdatedAtDesc(Long teacherId);

    Optional<CourseClass> findByJoinCode(String joinCode);

    boolean existsByJoinCode(String joinCode);
}
