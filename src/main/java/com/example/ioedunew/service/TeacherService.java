package com.example.ioedunew.service;

import com.example.ioedunew.common.BusinessException;
import com.example.ioedunew.entity.Enrollment;
import com.example.ioedunew.entity.Project;
import com.example.ioedunew.entity.User;
import com.example.ioedunew.repository.EnrollmentRepository;
import com.example.ioedunew.repository.ProjectRepository;
import com.example.ioedunew.repository.UserRepository;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * 教师端服务:讲师管理自己名下项目的教学资源、封面与学生进度。
 * 归属边界:非管理员只能操作 mentorId 等于自己的项目,越权访问返回 403。
 */
@Service
public class TeacherService {

    private final ProjectRepository projectRepository;
    private final EnrollmentRepository enrollmentRepository;
    private final UserRepository userRepository;
    private final ObjectMapper objectMapper;

    public TeacherService(ProjectRepository projectRepository,
                          EnrollmentRepository enrollmentRepository,
                          UserRepository userRepository,
                          ObjectMapper objectMapper) {
        this.projectRepository = projectRepository;
        this.enrollmentRepository = enrollmentRepository;
        this.userRepository = userRepository;
        this.objectMapper = objectMapper;
    }

    /** 我的项目:管理员看全部,教师只看自己名下 */
    public List<Project> myProjects(Long userId, boolean admin) {
        return projectRepository.findAll().stream()
                .filter(p -> admin || userId.equals(p.getMentorId()))
                .sorted((a, b) -> b.getUpdatedAt().compareTo(a.getUpdatedAt()))
                .collect(Collectors.toList());
    }

    /** 教学工作台统计:项目数 / 报名学生数 / 资源文件数 */
    public Map<String, Object> stats(Long userId, boolean admin) {
        List<Project> mine = myProjects(userId, admin);
        long students = mine.stream().mapToLong(p -> p.getEnrolledCount() == null ? 0 : p.getEnrolledCount()).sum();
        int resources = mine.stream().mapToInt(this::countResources).sum();
        double rating = mine.stream().mapToDouble(p -> p.getRating() == null ? 0 : p.getRating())
                .average().orElse(0);

        Map<String, Object> m = new HashMap<>();
        m.put("projectCount", mine.size());
        m.put("studentTotal", students);
        m.put("resourceCount", resources);
        m.put("avgRating", Math.round(rating * 10) / 10.0);
        return m;
    }

    /**
     * 更新项目教学资源。
     * resources 为 JSON 数组文本 [{type,name,url}],保存前校验 JSON 合法性;
     * url 为空表示该资源尚未上传附件。
     */
    @Transactional
    public Project updateResources(Long userId, boolean admin, Long projectId, String resources) {
        Project p = ownedProject(userId, admin, projectId);
        try {
            JsonNode node = objectMapper.readTree(resources == null || resources.isEmpty() ? "[]" : resources);
            if (!node.isArray()) {
                throw new BusinessException("资源列表必须是 JSON 数组");
            }
        } catch (BusinessException e) {
            throw e;
        } catch (Exception e) {
            throw new BusinessException("资源列表不是合法 JSON");
        }
        p.setResources(resources == null || resources.isEmpty() ? "[]" : resources);
        p.setUpdatedAt(LocalDateTime.now());
        return projectRepository.save(p);
    }

    /** 更新项目封面 */
    @Transactional
    public Project updateCover(Long userId, boolean admin, Long projectId, String coverUrl) {
        Project p = ownedProject(userId, admin, projectId);
        p.setCoverUrl(coverUrl == null || coverUrl.isEmpty() ? null : coverUrl);
        p.setUpdatedAt(LocalDateTime.now());
        return projectRepository.save(p);
    }

    /** 项目报名学生与进度(附学生姓名/学号) */
    public List<Map<String, Object>> projectStudents(Long userId, boolean admin, Long projectId) {
        ownedProject(userId, admin, projectId);
        List<Map<String, Object>> result = new ArrayList<>();
        for (Enrollment e : enrollmentRepository.findByProjectIdOrderByEnrolledAtDesc(projectId)) {
            User u = userRepository.findById(e.getUserId()).orElse(null);
            Map<String, Object> m = new HashMap<>();
            m.put("enrollmentId", e.getId());
            m.put("studentName", u == null ? "已注销用户" : u.getName());
            m.put("studentNo", u == null ? "-" : u.getStudentNo());
            m.put("major", u == null ? "-" : u.getMajor());
            m.put("progress", e.getProgress());
            m.put("currentTask", e.getCurrentTask());
            m.put("status", e.getStatus());
            m.put("deadline", e.getDeadline());
            m.put("enrolledAt", e.getEnrolledAt());
            result.add(m);
        }
        return result;
    }

    private Project ownedProject(Long userId, boolean admin, Long projectId) {
        Project p = projectRepository.findById(projectId)
                .orElseThrow(() -> new BusinessException(404, "项目不存在"));
        if (!admin && !userId.equals(p.getMentorId())) {
            throw new BusinessException(403, "该项目不属于你,无权操作");
        }
        return p;
    }

    private int countResources(Project p) {
        try {
            JsonNode node = objectMapper.readTree(p.getResources() == null ? "[]" : p.getResources());
            return node.isArray() ? node.size() : 0;
        } catch (Exception e) {
            return 0;
        }
    }
}
