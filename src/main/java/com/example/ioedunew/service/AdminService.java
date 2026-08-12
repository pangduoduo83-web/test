package com.example.ioedunew.service;

import com.example.ioedunew.common.BusinessException;
import com.example.ioedunew.dto.AdminDtos;
import com.example.ioedunew.entity.Discussion;
import com.example.ioedunew.entity.Enrollment;
import com.example.ioedunew.entity.Equipment;
import com.example.ioedunew.entity.Project;
import com.example.ioedunew.entity.SkillScore;
import com.example.ioedunew.entity.User;
import com.example.ioedunew.repository.BorrowRequestRepository;
import com.example.ioedunew.repository.DiscussionRepository;
import com.example.ioedunew.repository.EquipmentFavoriteRepository;
import com.example.ioedunew.repository.EquipmentRepository;
import com.example.ioedunew.repository.EnrollmentRepository;
import com.example.ioedunew.repository.FavoriteRepository;
import com.example.ioedunew.repository.NotificationRepository;
import com.example.ioedunew.repository.ProjectRepository;
import com.example.ioedunew.repository.SkillScoreRepository;
import com.example.ioedunew.repository.SubmissionRepository;
import com.example.ioedunew.repository.UserRepository;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.mindrot.jbcrypt.BCrypt;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * 管理端服务:设备/项目 CRUD、用户管理与数据看板统计。
 * 设备删除边界:存在未归还借阅时禁止删除,避免库存对不上。
 */
@Service
public class AdminService {

    private final EquipmentRepository equipmentRepository;
    private final ProjectRepository projectRepository;
    private final UserRepository userRepository;
    private final BorrowRequestRepository borrowRepository;
    private final EnrollmentRepository enrollmentRepository;
    private final SubmissionRepository submissionRepository;
    private final FavoriteRepository favoriteRepository;
    private final DiscussionRepository discussionRepository;
    private final SkillScoreRepository skillScoreRepository;
    private final NotificationRepository notificationRepository;
    private final EquipmentFavoriteRepository equipmentFavoriteRepository;
    private final ProjectService projectService;
    private final NotificationService notificationService;
    private final ObjectMapper objectMapper;

    public AdminService(EquipmentRepository equipmentRepository,
                        ProjectRepository projectRepository,
                        UserRepository userRepository,
                        BorrowRequestRepository borrowRepository,
                        EnrollmentRepository enrollmentRepository,
                        SubmissionRepository submissionRepository,
                        FavoriteRepository favoriteRepository,
                        DiscussionRepository discussionRepository,
                        SkillScoreRepository skillScoreRepository,
                        NotificationRepository notificationRepository,
                        EquipmentFavoriteRepository equipmentFavoriteRepository,
                        ProjectService projectService,
                        NotificationService notificationService,
                        ObjectMapper objectMapper) {
        this.equipmentRepository = equipmentRepository;
        this.projectRepository = projectRepository;
        this.userRepository = userRepository;
        this.borrowRepository = borrowRepository;
        this.enrollmentRepository = enrollmentRepository;
        this.submissionRepository = submissionRepository;
        this.favoriteRepository = favoriteRepository;
        this.discussionRepository = discussionRepository;
        this.skillScoreRepository = skillScoreRepository;
        this.notificationRepository = notificationRepository;
        this.equipmentFavoriteRepository = equipmentFavoriteRepository;
        this.projectService = projectService;
        this.notificationService = notificationService;
        this.objectMapper = objectMapper;
    }

    // ---------- 设备 ----------

    @Transactional
    public Equipment saveEquipment(Equipment input) {
        normalizeEquipmentJson(input);
        input.setDescription(com.example.ioedunew.common.HtmlSanitizer.clean(input.getDescription()));
        if (input.getId() != null) {
            Equipment existing = equipmentRepository.findById(input.getId())
                    .orElseThrow(() -> new BusinessException(404, "设备不存在"));
            input.setCreatedAt(existing.getCreatedAt());
            input.setBorrowCount(existing.getBorrowCount());
        } else {
            input.setBorrowCount(0);
        }
        if (input.getAvailableCount() == null || input.getAvailableCount() > input.getTotalCount()) {
            input.setAvailableCount(input.getTotalCount());
        }
        return equipmentRepository.save(input);
    }

    @Transactional
    public void deleteEquipment(Long id) {
        boolean hasActive = borrowRepository.findAllByOrderByAppliedAtDesc().stream()
                .anyMatch(b -> b.getEquipmentId().equals(id)
                        && ("APPROVED".equals(b.getStatus()) || "RETURN_REQUESTED".equals(b.getStatus())
                        || "PENDING".equals(b.getStatus())));
        if (hasActive) {
            throw new BusinessException("该设备存在进行中的借阅,不能删除");
        }
        equipmentFavoriteRepository.deleteByEquipmentId(id);
        equipmentRepository.deleteById(id);
    }

    // ---------- 项目 ----------

    @Transactional
    public Project saveProject(Project input) {
        normalizeProjectJson(input);
        input.setDescription(com.example.ioedunew.common.HtmlSanitizer.clean(input.getDescription()));
        if (input.getId() != null) {
            Project existing = projectRepository.findById(input.getId())
                    .orElseThrow(() -> new BusinessException(404, "项目不存在"));
            input.setCreatedAt(existing.getCreatedAt());
            input.setEnrolledCount(existing.getEnrolledCount());
            input.setViews(existing.getViews());
            input.setFavoriteCount(existing.getFavoriteCount());
            input.setDownloads(existing.getDownloads());
            // 统计量一律保留旧值:实体字段默认 0,payload 不带时反序列化得到 0 而非 null,
            // 若不强制保留会把已有统计清零
            input.setCompletionRate(existing.getCompletionRate());
            input.setForks(existing.getForks());
        }
        input.setUpdatedAt(LocalDateTime.now());
        return projectRepository.save(input);
    }

    @Transactional
    public void deleteProject(Long id) {
        projectRepository.deleteById(id);
    }

    // ---------- 用户 ----------

    public List<User> listUsers(String keyword, String role, Boolean enabled) {
        String kw = keyword == null ? "" : keyword.trim().toLowerCase();
        return userRepository.findAll().stream()
                .filter(u -> kw.isEmpty() || contains(u.getName(), kw) || contains(u.getEmail(), kw)
                        || contains(u.getStudentNo(), kw) || contains(u.getMajor(), kw))
                .filter(u -> role == null || role.trim().isEmpty() || "ALL".equalsIgnoreCase(role)
                        || role.equalsIgnoreCase(u.getRole()))
                .filter(u -> enabled == null || enabled.equals(u.getEnabled()))
                .sorted(Comparator.comparing(User::getCreatedAt,
                        Comparator.nullsLast(Comparator.reverseOrder())))
                .collect(Collectors.toList());
    }

    public User getUser(Long id) {
        return requireUser(id);
    }

    @Transactional
    public User createUser(AdminDtos.UserCreateRequest req) {
        String email = normalizeEmail(req.getEmail());
        if (userRepository.existsByEmail(email)) {
            throw new BusinessException("该邮箱已被使用");
        }
        User user = new User();
        user.setName(requireText(req.getName(), "姓名"));
        user.setEmail(email);
        user.setPasswordHash(BCrypt.hashpw(req.getPassword(), BCrypt.gensalt()));
        user.setStudentNo(cleanNullable(req.getStudentNo()));
        user.setMajor(cleanNullable(req.getMajor()));
        user.setGrade(cleanNullable(req.getGrade()));
        user.setAvatarUrl(cleanNullable(req.getAvatarUrl()));
        user.setRole(normalizeRole(req.getRole()));
        user.setEnabled(req.getEnabled() == null || req.getEnabled());
        userRepository.save(user);

        for (String dimension : AuthService.SKILL_DIMENSIONS) {
            SkillScore score = new SkillScore();
            score.setUserId(user.getId());
            score.setSkillName(dimension);
            score.setScore(30);
            skillScoreRepository.save(score);
        }
        return user;
    }

    @Transactional
    public User updateUser(Long id, AdminDtos.UserUpdateRequest req) {
        User user = requireUser(id);
        if (req.getName() != null) {
            user.setName(requireText(req.getName(), "姓名"));
        }
        if (req.getEmail() != null) {
            String email = normalizeEmail(req.getEmail());
            User sameEmail = userRepository.findByEmail(email).orElse(null);
            if (sameEmail != null && !sameEmail.getId().equals(id)) {
                throw new BusinessException("该邮箱已被使用");
            }
            user.setEmail(email);
        }
        if (req.getStudentNo() != null) {
            user.setStudentNo(cleanNullable(req.getStudentNo()));
        }
        if (req.getMajor() != null) {
            user.setMajor(cleanNullable(req.getMajor()));
        }
        if (req.getGrade() != null) {
            user.setGrade(cleanNullable(req.getGrade()));
        }
        if (req.getAvatarUrl() != null) {
            user.setAvatarUrl(cleanNullable(req.getAvatarUrl()));
        }
        if (req.getEnabled() != null) {
            user.setEnabled(req.getEnabled());
        }
        if (req.getRole() != null) {
            String role = normalizeRole(req.getRole());
            if ("ADMIN".equals(user.getRole()) && !"ADMIN".equals(role)
                    && userRepository.countByRole("ADMIN") <= 1) {
                throw new BusinessException(409, "不能变更最后一个管理员的角色");
            }
            user.setRole(role);
        }
        return userRepository.save(user);
    }

    @Transactional
    public void resetPassword(Long id, String password) {
        User user = requireUser(id);
        user.setPasswordHash(BCrypt.hashpw(password, BCrypt.gensalt()));
        userRepository.save(user);
    }

    @Transactional
    public void deleteUser(Long id, Long currentAdminId) {
        User user = requireUser(id);
        if (id.equals(currentAdminId)) {
            throw new BusinessException(409, "不能删除当前登录账号");
        }
        if ("ADMIN".equals(user.getRole()) && userRepository.countByRole("ADMIN") <= 1) {
            throw new BusinessException(409, "不能删除最后一个管理员");
        }
        if (borrowRepository.existsByUserId(id) || enrollmentRepository.existsByUserId(id)
                || submissionRepository.existsByUserId(id) || projectRepository.existsByMentorId(id)) {
            throw new BusinessException(409, "该用户存在借阅、报名、成果或导师项目等核心关联,请改为禁用账号");
        }
        favoriteRepository.deleteByUserId(id);
        equipmentFavoriteRepository.deleteByUserId(id);
        discussionRepository.deleteByUserId(id);
        skillScoreRepository.deleteByUserId(id);
        notificationRepository.deleteByUserId(id);
        userRepository.delete(user);
    }

    // ---------- 报名与进度 ----------

    public List<Enrollment> listEnrollments(Long projectId, Long userId, String status) {
        return enrollmentRepository.findAll().stream()
                .filter(e -> projectId == null || projectId.equals(e.getProjectId()))
                .filter(e -> userId == null || userId.equals(e.getUserId()))
                .filter(e -> status == null || status.trim().isEmpty() || "ALL".equalsIgnoreCase(status)
                        || status.equalsIgnoreCase(e.getStatus()))
                .sorted(Comparator.comparing(Enrollment::getEnrolledAt,
                        Comparator.nullsLast(Comparator.reverseOrder())))
                .collect(Collectors.toList());
    }

    @Transactional
    public Enrollment createEnrollment(AdminDtos.EnrollmentCreateRequest req) {
        User user = requireUser(req.getUserId());
        if (!"STUDENT".equals(user.getRole())) {
            throw new BusinessException("只能为学生代报名");
        }
        if (!Boolean.TRUE.equals(user.getEnabled())) {
            throw new BusinessException("该学生账号已禁用");
        }
        return projectService.enroll(req.getUserId(), req.getProjectId());
    }

    @Transactional
    public Enrollment updateEnrollment(Long id, AdminDtos.EnrollmentUpdateRequest req) {
        Enrollment enrollment = enrollmentRepository.findById(id)
                .orElseThrow(() -> new BusinessException(404, "报名记录不存在"));
        boolean wasCompleted = "COMPLETED".equals(enrollment.getStatus());
        if (req.getProgress() != null) {
            enrollment.setProgress(req.getProgress());
            if (req.getProgress() >= 100 && !wasCompleted) {
                enrollment.setStatus("COMPLETED");
                userRepository.findById(enrollment.getUserId()).ifPresent(u -> {
                    u.setExp(u.getExp() + 50);
                    userRepository.save(u);
                });
                notificationService.create(enrollment.getUserId(), "project", "项目完成",
                        "管理员已将《" + enrollment.getProjectTitle() + "》进度更新为完成,经验值 +50!");
            }
        }
        if (req.getCurrentTask() != null) {
            enrollment.setCurrentTask(cleanNullable(req.getCurrentTask()));
        }
        enrollment.setDeadline(req.getDeadline());
        return enrollmentRepository.save(enrollment);
    }

    @Transactional
    public void deleteEnrollment(Long id) {
        Enrollment enrollment = enrollmentRepository.findById(id)
                .orElseThrow(() -> new BusinessException(404, "报名记录不存在"));
        if (submissionRepository.existsByUserIdAndProjectId(
                enrollment.getUserId(), enrollment.getProjectId())) {
            throw new BusinessException(409, "该报名已有成果提交,不能删除");
        }
        Project project = projectRepository.findById(enrollment.getProjectId()).orElse(null);
        if (project != null) {
            int count = project.getEnrolledCount() == null ? 0 : project.getEnrolledCount();
            project.setEnrolledCount(Math.max(0, count - 1));
            projectRepository.save(project);
        }
        enrollmentRepository.delete(enrollment);
    }

    // ---------- 数据看板 ----------

    public Map<String, Object> stats() {
        Map<String, Object> m = new HashMap<>();
        m.put("studentCount", userRepository.countByRole("STUDENT"));
        m.put("equipmentCount", equipmentRepository.count());
        m.put("projectCount", projectRepository.count());
        m.put("pendingBorrows", borrowRepository.countByStatus("PENDING"));
        m.put("activeBorrows", borrowRepository.countByStatus("APPROVED")
                + borrowRepository.countByStatus("RETURN_REQUESTED"));
        m.put("returnRequests", borrowRepository.countByStatus("RETURN_REQUESTED"));
        m.put("recentPending", borrowRepository.findByStatusOrderByAppliedAtDesc("PENDING").stream()
                .limit(5).toArray());
        return m;
    }

    /**
     * 报表数据:近 30 天申请/归还趋势 + 设备利用率排行(按累计借出次数取前 8)。
     */
    public Map<String, Object> trends() {
        java.time.LocalDate today = java.time.LocalDate.now();
        java.util.List<String> days = new java.util.ArrayList<>();
        java.util.List<Long> applied = new java.util.ArrayList<>();
        java.util.List<Long> returned = new java.util.ArrayList<>();
        java.util.List<com.example.ioedunew.entity.BorrowRequest> all = borrowRepository.findAll();
        for (int i = 29; i >= 0; i--) {
            java.time.LocalDate day = today.minusDays(i);
            days.add(day.format(java.time.format.DateTimeFormatter.ofPattern("MM-dd")));
            applied.add(all.stream()
                    .filter(b -> b.getAppliedAt() != null && b.getAppliedAt().toLocalDate().equals(day))
                    .count());
            returned.add(all.stream()
                    .filter(b -> b.getReturnedAt() != null && b.getReturnedAt().toLocalDate().equals(day))
                    .count());
        }

        java.util.List<Map<String, Object>> utilization = equipmentRepository.findAll().stream()
                .sorted(java.util.Comparator.comparing(Equipment::getBorrowCount).reversed())
                .limit(8)
                .map(e -> {
                    Map<String, Object> m = new HashMap<>();
                    m.put("name", e.getName());
                    m.put("borrowCount", e.getBorrowCount());
                    m.put("inUse", e.getTotalCount() - e.getAvailableCount());
                    m.put("total", e.getTotalCount());
                    m.put("inUseRate", e.getTotalCount() == 0 ? 0
                            : Math.round((e.getTotalCount() - e.getAvailableCount()) * 100.0 / e.getTotalCount()));
                    return m;
                })
                .collect(java.util.stream.Collectors.toList());

        Map<String, Object> result = new HashMap<>();
        result.put("days", days);
        result.put("applied", applied);
        result.put("returned", returned);
        result.put("utilization", utilization);
        return result;
    }

    private User requireUser(Long id) {
        return userRepository.findById(id)
                .orElseThrow(() -> new BusinessException(404, "用户不存在"));
    }

    private String normalizeEmail(String email) {
        String value = requireText(email, "邮箱").toLowerCase();
        if (!value.contains("@")) {
            throw new BusinessException("邮箱格式不正确");
        }
        return value;
    }

    private String normalizeRole(String role) {
        String value = role == null || role.trim().isEmpty()
                ? "STUDENT" : role.trim().toUpperCase();
        if (!"STUDENT".equals(value) && !"TEACHER".equals(value) && !"ADMIN".equals(value)) {
            throw new BusinessException("非法角色:" + role);
        }
        return value;
    }

    private String requireText(String value, String label) {
        if (value == null || value.trim().isEmpty()) {
            throw new BusinessException(label + "不能为空");
        }
        return value.trim();
    }

    private String cleanNullable(String value) {
        return value == null || value.trim().isEmpty() ? null : value.trim();
    }

    // ---------- 讨论管理 ----------

    public List<Discussion> listDiscussions(Long projectId, String keyword) {
        String kw = keyword == null ? "" : keyword.trim().toLowerCase();
        return discussionRepository.findAllByOrderByCreatedAtDesc().stream()
                .filter(d -> projectId == null || projectId.equals(d.getProjectId()))
                .filter(d -> kw.isEmpty() || contains(d.getContent(), kw) || contains(d.getUserName(), kw))
                .collect(Collectors.toList());
    }

    /**
     * 删除讨论:删除主题帖时级联删除其全部回复,返回实际删除的条数。
     */
    @Transactional
    public int deleteDiscussion(Long id) {
        Discussion discussion = discussionRepository.findById(id)
                .orElseThrow(() -> new BusinessException(404, "讨论不存在"));
        int removed = 1;
        if (discussion.getParentId() == null) {
            List<Discussion> replies = discussionRepository.findByParentId(discussion.getId());
            removed += replies.size();
            discussionRepository.deleteAll(replies);
        }
        discussionRepository.delete(discussion);
        return removed;
    }

    private boolean contains(String value, String keyword) {
        return value != null && value.toLowerCase().contains(keyword);
    }

    // ---------- JSON 字段兜底 ----------

    private void normalizeEquipmentJson(Equipment e) {
        e.setSpecs(orEmptyArray(e.getSpecs()));
        e.setTags(orEmptyArray(e.getTags()));
        e.setDocs(orEmptyArray(e.getDocs()));
        e.setSuitableProjects(orEmptyArray(e.getSuitableProjects()));
    }

    private void normalizeProjectJson(Project p) {
        p.setTags(requireJsonArray(p.getTags(), "标签"));
        p.setFeatures(requireJsonArray(p.getFeatures(), "项目特性"));
        p.setLearningGoals(requireJsonArray(p.getLearningGoals(), "学习目标"));
        p.setPrerequisites(requireJsonArray(p.getPrerequisites(), "前置要求"));
        p.setSkillRequirements(requireJsonArray(p.getSkillRequirements(), "技能要求"));
        p.setSyllabus(requireJsonArray(p.getSyllabus(), "教学大纲"));
        p.setBom(requireJsonArray(p.getBom(), "BOM清单"));
        p.setResources(requireJsonArray(p.getResources(), "学习资源"));
        p.setEquipmentNames(requireJsonArray(p.getEquipmentNames(), "所需设备"));
        p.setAssessments(requireJsonArray(p.getAssessments(), "成果考核项"));
    }

    private String orEmptyArray(String json) {
        return (json == null || json.trim().isEmpty()) ? "[]" : json;
    }

    /** 空值兜底为 "[]",非空时校验必须是合法 JSON 且为数组,否则拒绝保存(学生端按数组解析展示)。 */
    private String requireJsonArray(String json, String label) {
        String value = orEmptyArray(json);
        try {
            JsonNode node = objectMapper.readTree(value);
            if (!node.isArray()) {
                throw new BusinessException("「" + label + "」必须是 JSON 数组");
            }
        } catch (BusinessException e) {
            throw e;
        } catch (Exception e) {
            throw new BusinessException("「" + label + "」不是合法 JSON");
        }
        return value;
    }
}
