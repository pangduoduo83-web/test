package com.example.ioedunew.repository;

import com.example.ioedunew.entity.ClassTeacher;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface ClassTeacherRepository extends JpaRepository<ClassTeacher, Long> {
    List<ClassTeacher> findByClassIdOrderByCreatedAtAsc(Long classId);

    List<ClassTeacher> findByTeacherId(Long teacherId);

    Optional<ClassTeacher> findByClassIdAndTeacherId(Long classId, Long teacherId);

    boolean existsByClassIdAndTeacherId(Long classId, Long teacherId);

    void deleteByClassId(Long classId);
}
