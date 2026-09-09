package com.example.ioedunew.service;

import com.example.ioedunew.common.BusinessException;
import com.example.ioedunew.entity.Enrollment;
import com.example.ioedunew.entity.Project;
import com.example.ioedunew.entity.User;
import com.example.ioedunew.repository.EnrollmentRepository;
import com.example.ioedunew.repository.ProjectRepository;
import com.example.ioedunew.repository.SubmissionRepository;
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
    private final SubmissionRepository submissionRepository;
    private final AdminService adminService;
    private final ObjectMapper objectMapper;
    private final NotificationService notificationService;
    private final com.example.ioedunew.repository.LearningActivityRepository activityRepository;

    public TeacherService(ProjectRepository projectRepository,
                          EnrollmentRepository enrollmentRepository,
                          UserRepository userRepository,
                          SubmissionRepository submissionRepository,
                          AdminService adminService,
                          ObjectMapper objectMapper,
                          NotificationService notificationService,
                          com.example.ioedunew.repository.LearningActivityRepository activityRepository) {
        this.projectRepository = projectRepository;
        this.enrollmentRepository = enrollmentRepository;
        this.userRepository = userRepository;
        this.submissionRepository = submissionRepository;
        this.adminService = adminService;
        this.objectMapper = objectMapper;
        this.notificationService = notificationService;
        this.activityRepository = activityRepository;
    }

    /** 我的项目:管理员看全部,教师只看自己名下 */
    public List<Project> myProjects(Long userId, boolean admin) {
        return projectRepository.findAll().stream()
                .filter(p -> admin || userId.equals(p.getMentorId()))
                .sorted((a, b) -> b.getUpdatedAt().compareTo(a.getUpdatedAt()))
                .collect(Collectors.toList());
    }

    /** 教学工作台统计:项目数 / 报名学生数 / 待评成果数 / 已完成学生数 */
    public Map<String, Object> stats(Long userId, boolean admin) {
        List<Project> mine = myProjects(userId, admin);
        java.util.Set<Long> ids = mine.stream().map(Project::getId).collect(Collectors.toSet());
        long students = 0;
        long completed = 0;
        for (Long id : ids) {
            for (Enrollment e : enrollmentRepository.findByProjectIdOrderByEnrolledAtDesc(id)) {
                students++;
                if ("COMPLETED".equals(e.getStatus())) {
                    completed++;
                }
            }
        }
        long pending = submissionRepository.findByStatusOrderBySubmittedAtDesc("SUBMITTED").stream()
                .filter(s -> ids.contains(s.getProjectId())).count();

        Map<String, Object> m = new HashMap<>();
        m.put("projectCount", mine.size());
        m.put("studentTotal", students);
        m.put("completedTotal", completed);
        m.put("pendingSubmissions", pending);
        m.put("atRiskCount", atRisk(userId, admin).size());
        m.put("resourceCount", mine.stream().mapToInt(this::countResources).sum());
        return m;
    }

    /**
     * 教师编辑自己项目的教学内容。统计字段、讲师归属与创建时间沿用原值;
     * 描述经 HtmlSanitizer 消毒,与管理端保存口径一致。
     */
    @Transactional
    public Project updateProject(Long userId, boolean admin, Long projectId, Project input) {
        Project existing = ownedProject(userId, admin, projectId);
        if (input.getTitle() == null || input.getTitle().trim().isEmpty()) {
            throw new BusinessException("项目标题不能为空");
        }
        input.setId(projectId);
        input.setMentor(existing.getMentor());
        input.setMentorId(existing.getMentorId());
        input.setHubItemId(existing.getHubItemId());
        input.setHubVersionNo(existing.getHubVersionNo());
        return adminService.saveProject(input);
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

    /** 给项目的全部报名学生发公告(站内通知) */
    @Transactional
    public int announceToProject(Long userId, boolean admin, Long projectId, String title, String content) {
        Project p = ownedProject(userId, admin, projectId);
        if (title == null || title.trim().isEmpty() || title.trim().length() > 100) {
            throw new BusinessException("公告标题需为 1~100 字");
        }
        String author = userRepository.findById(userId).map(User::getName).orElse("教师");
        String body = (content == null || content.trim().isEmpty() ? "" : content.trim() + " ") + "—— 《" + p.getTitle() + "》· " + author;
        int n = 0;
        for (Enrollment e : enrollmentRepository.findByProjectIdOrderByEnrolledAtDesc(projectId)) {
            notificationService.create(e.getUserId(), "project", "项目公告:" + title.trim(), body);
            n++;
        }
        return n;
    }

    /** 教师新建自己指导的项目(默认草稿,编辑满意后再发布) */
    @Transactional
    public Project createProject(Long userId, Project input) {
        if (input.getTitle() == null || input.getTitle().trim().isEmpty()) {
            throw new BusinessException("项目标题不能为空");
        }
        User me = userRepository.findById(userId).orElseThrow(() -> new BusinessException(401, "用户不存在"));
        input.setId(null);
        input.setMentorId(me.getId());
        input.setMentor(me.getName());
        if (input.getStatus() == null || input.getStatus().isEmpty()) {
            input.setStatus("DRAFT");
        }
        return adminService.saveProject(input);
    }

    /** 定向提醒某个学生(站内通知) */
    @Transactional
    public void remindStudent(Long userId, boolean admin, Long projectId, Long studentId, String message) {
        Project p = ownedProject(userId, admin, projectId);
        enrollmentRepository.findByUserIdAndProjectId(studentId, projectId)
                .orElseThrow(() -> new BusinessException(404, "该学生未报名此项目"));
        String author = userRepository.findById(userId).map(User::getName).orElse("老师");
        String body = (message == null || message.trim().isEmpty()
                ? "请尽快推进《" + p.getTitle() + "》的学习,有困难可以在项目讨论区或 AI 导师那里寻求帮助。"
                : message.trim()) + " —— " + author;
        notificationService.create(studentId, "project", "老师提醒:《" + p.getTitle() + "》", body);
    }

    /**
     * 掉队名单:我指导项目里进行中的报名,满足任一条件即列出——
     * 已过截止、14 天没有任何学习动作、时间过半但进度不到 30%。
     */
    public List<Map<String, Object>> atRisk(Long userId, boolean admin) {
        List<Map<String, Object>> out = new ArrayList<>();
        java.time.LocalDate today = java.time.LocalDate.now();
        for (Project p : myProjects(userId, admin)) {
            for (Enrollment e : enrollmentRepository.findByProjectIdOrderByEnrolledAtDesc(p.getId())) {
                if ("COMPLETED".equals(e.getStatus())) {
                    continue;
                }
                List<String> reasons = new ArrayList<>();
                boolean overdue = e.getDeadline() != null && e.getDeadline().isBefore(today);
                if (overdue) {
                    reasons.add("已过截止 " + java.time.temporal.ChronoUnit.DAYS.between(e.getDeadline(), today) + " 天");
                }
                LocalDateTime last = activityRepository.findTopByUserIdOrderByCreatedAtDesc(e.getUserId())
                        .map(a -> a.getCreatedAt()).orElse(e.getEnrolledAt());
                long idleDays = java.time.temporal.ChronoUnit.DAYS.between(last.toLocalDate(), today);
                if (idleDays >= 14) {
                    reasons.add(idleDays + " 天没有学习动作");
                }
                if (e.getDeadline() != null && e.getEnrolledAt() != null) {
                    long total = java.time.temporal.ChronoUnit.DAYS.between(e.getEnrolledAt().toLocalDate(), e.getDeadline());
                    long passed = java.time.temporal.ChronoUnit.DAYS.between(e.getEnrolledAt().toLocalDate(), today);
                    if (!overdue && total > 0 && passed * 2 >= total && (e.getProgress() == null ? 0 : e.getProgress()) < 30) {
                        reasons.add("时间过半进度仅 " + (e.getProgress() == null ? 0 : e.getProgress()) + "%");
                    }
                }
                if (reasons.isEmpty()) {
                    continue;
                }
                User u = userRepository.findById(e.getUserId()).orElse(null);
                Map<String, Object> m = new HashMap<>();
                m.put("userId", e.getUserId());
                m.put("studentName", u == null ? "已注销用户" : u.getName());
                m.put("studentNo", u == null ? null : u.getStudentNo());
                m.put("projectId", p.getId());
                m.put("projectTitle", p.getTitle());
                m.put("progress", e.getProgress());
                m.put("currentTask", e.getCurrentTask());
                m.put("deadline", e.getDeadline());
                m.put("lastActiveAt", last);
                m.put("reasons", reasons);
                out.add(m);
            }
        }
        return out;
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
