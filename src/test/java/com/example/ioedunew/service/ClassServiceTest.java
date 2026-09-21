package com.example.ioedunew.service;

import com.example.ioedunew.common.BusinessException;
import com.example.ioedunew.config.AuthUser;
import com.example.ioedunew.entity.ClassTeacher;
import com.example.ioedunew.entity.CourseClass;
import com.example.ioedunew.entity.User;
import com.example.ioedunew.repository.ClassAnnouncementRepository;
import com.example.ioedunew.repository.ClassAssignmentRepository;
import com.example.ioedunew.repository.ClassMemberRepository;
import com.example.ioedunew.repository.ClassTeacherRepository;
import com.example.ioedunew.repository.CourseClassRepository;
import com.example.ioedunew.repository.EnrollmentRepository;
import com.example.ioedunew.repository.ProjectRepository;
import com.example.ioedunew.repository.UserRepository;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class ClassServiceTest {

    private final CourseClassRepository classes = mock(CourseClassRepository.class);
    private final ClassMemberRepository members = mock(ClassMemberRepository.class);
    private final ClassTeacherRepository teachers = mock(ClassTeacherRepository.class);
    private final ClassAssignmentRepository assignments = mock(ClassAssignmentRepository.class);
    private final ClassAnnouncementRepository announcements = mock(ClassAnnouncementRepository.class);
    private final UserRepository users = mock(UserRepository.class);
    private final ClassService service = new ClassService(classes, members, teachers, assignments, announcements, users,
            mock(ProjectRepository.class), mock(EnrollmentRepository.class), mock(NotificationService.class),
            mock(ProjectStatsService.class), mock(LearningActivityService.class));

    @Test
    void coTeacherCanSeeClassButCannotChangeClassSettings() {
        CourseClass c = courseClass(11L, 7L);
        ClassTeacher link = link(11L, 8L, "TEACHER");
        when(teachers.findByTeacherId(8L)).thenReturn(Collections.singletonList(link));
        when(classes.findAllById(any())).thenReturn(Collections.singletonList(c));
        when(members.countByClassId(11L)).thenReturn(2L);
        when(assignments.countByClassId(11L)).thenReturn(1L);
        when(teachers.findByClassIdOrderByCreatedAtAsc(11L)).thenReturn(Collections.singletonList(link));
        assertEquals(1, service.list(new AuthUser(8L, "TEACHER", "tenant-a")).size());

        when(classes.findById(11L)).thenReturn(Optional.of(c));
        when(teachers.existsByClassIdAndTeacherId(11L, 8L)).thenReturn(true);
        Map<String, Object> update = new HashMap<>();
        update.put("name", "不允许协作教师改班级名称");
        assertThrows(BusinessException.class, () -> service.update(new AuthUser(8L, "TEACHER", "tenant-a"), 11L, update));
    }

    @Test
    void ownerCanAddTeacherByEmail() {
        CourseClass c = courseClass(11L, 7L);
        User teacher = new User();
        teacher.setId(8L);
        teacher.setName("协作教师");
        teacher.setEmail("teacher@example.com");
        teacher.setRole("TEACHER");
        when(classes.findById(11L)).thenReturn(Optional.of(c));
        when(teachers.existsByClassIdAndTeacherId(11L, 8L)).thenReturn(false);
        when(users.findByEmail("teacher@example.com")).thenReturn(Optional.of(teacher));
        when(teachers.findByClassIdOrderByCreatedAtAsc(11L)).thenReturn(Collections.singletonList(link(11L, 7L, "OWNER")));
        when(users.findById(7L)).thenReturn(Optional.of(owner()));
        Map<String, Object> result = service.addTeachers(new AuthUser(7L, "TEACHER", "tenant-a"), 11L,
                Collections.singletonList("teacher@example.com"));
        assertEquals(Collections.singletonList("协作教师"), result.get("added"));
    }

    private static CourseClass courseClass(Long id, Long ownerId) {
        CourseClass c = new CourseClass();
        c.setId(id);
        c.setName("测试班级");
        c.setTeacherId(ownerId);
        c.setTeacherName("负责人");
        c.setJoinCode("ABC234");
        c.setCreatedAt(LocalDateTime.now());
        c.setUpdatedAt(LocalDateTime.now());
        return c;
    }

    private static ClassTeacher link(Long classId, Long teacherId, String role) {
        ClassTeacher link = new ClassTeacher();
        link.setClassId(classId);
        link.setTeacherId(teacherId);
        link.setRole(role);
        return link;
    }

    private static User owner() {
        User u = new User();
        u.setId(7L);
        u.setName("负责人");
        u.setEmail("owner@example.com");
        u.setRole("TEACHER");
        return u;
    }
}
