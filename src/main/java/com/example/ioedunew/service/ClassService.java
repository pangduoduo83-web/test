package com.example.ioedunew.service;

import com.example.ioedunew.common.BusinessException;
import com.example.ioedunew.config.AuthUser;
import com.example.ioedunew.entity.ClassAnnouncement;
import com.example.ioedunew.entity.ClassAssignment;
import com.example.ioedunew.entity.ClassMember;
import com.example.ioedunew.entity.CourseClass;
import com.example.ioedunew.entity.Enrollment;
import com.example.ioedunew.entity.LearningActivity;
import com.example.ioedunew.entity.Project;
import com.example.ioedunew.entity.User;
import com.example.ioedunew.repository.ClassAnnouncementRepository;
import com.example.ioedunew.repository.ClassAssignmentRepository;
import com.example.ioedunew.repository.ClassMemberRepository;
import com.example.ioedunew.repository.CourseClassRepository;
import com.example.ioedunew.repository.EnrollmentRepository;
import com.example.ioedunew.repository.ProjectRepository;
import com.example.ioedunew.repository.UserRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.security.SecureRandom;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * 课程班:建班 / 加入码 / 成员 / 按班布置项目 / 公告。
 * 归属边界:教师只能操作 teacherId 是自己的班;管理员可操作全部班并指定授课教师。
 * 布置项目 = 给全班每个成员建报名记录(已报名的沿用),统一写入班级截止日期;之后加入的成员也自动补齐。
 */
@Service
public class ClassService {

    private static final String CODE_ALPHABET = "ABCDEFGHJKMNPQRSTUVWXYZ23456789";
    private static final SecureRandom RANDOM = new SecureRandom();

    private final CourseClassRepository classRepo;
    private final ClassMemberRepository memberRepo;
    private final ClassAssignmentRepository assignmentRepo;
    private final ClassAnnouncementRepository announcementRepo;
    private final UserRepository userRepository;
    private final ProjectRepository projectRepository;
    private final EnrollmentRepository enrollmentRepository;
    private final NotificationService notificationService;
    private final ProjectStatsService statsService;
    private final LearningActivityService activityService;

    public ClassService(CourseClassRepository classRepo, ClassMemberRepository memberRepo,
                        ClassAssignmentRepository assignmentRepo, ClassAnnouncementRepository announcementRepo,
                        UserRepository userRepository, ProjectRepository projectRepository,
                        EnrollmentRepository enrollmentRepository, NotificationService notificationService,
                        ProjectStatsService statsService, LearningActivityService activityService) {
        this.classRepo = classRepo;
        this.memberRepo = memberRepo;
        this.assignmentRepo = assignmentRepo;
        this.announcementRepo = announcementRepo;
        this.userRepository = userRepository;
        this.projectRepository = projectRepository;
        this.enrollmentRepository = enrollmentRepository;
        this.notificationService = notificationService;
        this.statsService = statsService;
        this.activityService = activityService;
    }

    // ---------- 教师 / 管理员 ----------

    public List<Map<String, Object>> list(AuthUser viewer) {
        List<CourseClass> classes = viewer.isAdmin()
                ? classRepo.findAllByOrderByUpdatedAtDesc()
                : classRepo.findByTeacherIdOrderByUpdatedAtDesc(viewer.getId());
        List<Map<String, Object>> out = new ArrayList<>();
        for (CourseClass c : classes) {
            out.add(view(c));
        }
        return out;
    }

    @Transactional
    public Map<String, Object> create(AuthUser viewer, String name, String description, Long teacherId) {
        if (name == null || name.trim().isEmpty() || name.trim().length() > 60) {
            throw new BusinessException("班级名称需为 1~60 字");
        }
        CourseClass c = new CourseClass();
        c.setName(name.trim());
        c.setDescription(description == null ? null : cut(description.trim(), 300));
        Long tid = viewer.isAdmin() && teacherId != null ? teacherId : viewer.getId();
        User teacher = userRepository.findById(tid).orElseThrow(() -> new BusinessException(404, "授课教师不存在"));
        if (!"TEACHER".equals(teacher.getRole()) && !"ADMIN".equals(teacher.getRole())) {
            throw new BusinessException("授课教师必须是教师或管理员账号");
        }
        c.setTeacherId(teacher.getId());
        c.setTeacherName(teacher.getName());
        c.setJoinCode(newCode());
        c.setCreatedBy(viewer.getId());
        classRepo.save(c);
        return view(c);
    }

    @Transactional
    public Map<String, Object> update(AuthUser viewer, Long classId, Map<String, Object> body) {
        CourseClass c = owned(viewer, classId);
        if (body.get("name") != null) {
            String v = String.valueOf(body.get("name")).trim();
            if (v.isEmpty() || v.length() > 60) {
                throw new BusinessException("班级名称需为 1~60 字");
            }
            c.setName(v);
        }
        if (body.containsKey("description")) {
            c.setDescription(body.get("description") == null ? null : cut(String.valueOf(body.get("description")).trim(), 300));
        }
        if (body.containsKey("joinEnabled")) {
            c.setJoinEnabled(Boolean.TRUE.equals(body.get("joinEnabled")));
        }
        if (body.get("status") != null) {
            String st = String.valueOf(body.get("status"));
            if (!"ACTIVE".equals(st) && !"ARCHIVED".equals(st)) {
                throw new BusinessException("status 只能是 ACTIVE 或 ARCHIVED");
            }
            c.setStatus(st);
        }
        if (Boolean.TRUE.equals(body.get("regenerateCode"))) {
            c.setJoinCode(newCode());
        }
        if (viewer.isAdmin() && body.get("teacherId") != null) {
            Long tid = Long.valueOf(String.valueOf(body.get("teacherId")));
            User teacher = userRepository.findById(tid).orElseThrow(() -> new BusinessException(404, "授课教师不存在"));
            c.setTeacherId(teacher.getId());
            c.setTeacherName(teacher.getName());
        }
        c.setUpdatedAt(LocalDateTime.now());
        classRepo.save(c);
        return view(c);
    }

    /** 删除班级:只删班级、成员关系、作业与公告;学生的报名与成果不受影响 */
    @Transactional
    public void delete(AuthUser viewer, Long classId) {
        owned(viewer, classId);
        memberRepo.deleteByClassId(classId);
        assignmentRepo.deleteByClassId(classId);
        announcementRepo.deleteByClassId(classId);
        classRepo.deleteById(classId);
    }

    /** 班级详情:成员花名册(每个学生在每个作业上的进度)+ 作业 + 公告 */
    public Map<String, Object> detail(AuthUser viewer, Long classId) {
        CourseClass c = owned(viewer, classId);
        List<ClassAssignment> assignments = assignmentRepo.findByClassIdOrderByCreatedAtDesc(classId);
        List<Map<String, Object>> members = new ArrayList<>();
        for (ClassMember m : memberRepo.findByClassIdOrderByJoinedAtAsc(classId)) {
            User u = userRepository.findById(m.getUserId()).orElse(null);
            Map<String, Object> row = new LinkedHashMap<>();
            row.put("userId", m.getUserId());
            row.put("name", u == null ? "已注销用户" : u.getName());
            row.put("studentNo", u == null ? null : u.getStudentNo());
            row.put("major", u == null ? null : u.getMajor());
            row.put("joinedAt", m.getJoinedAt());
            row.put("lastActiveAt", activityService.latest(m.getUserId()).stream().findFirst().map(LearningActivity::getCreatedAt).orElse(null));
            List<Map<String, Object>> progress = new ArrayList<>();
            for (ClassAssignment a : assignments) {
                Enrollment e = enrollmentRepository.findByUserIdAndProjectId(m.getUserId(), a.getProjectId()).orElse(null);
                Map<String, Object> p = new LinkedHashMap<>();
                p.put("assignmentId", a.getId());
                p.put("projectId", a.getProjectId());
                p.put("progress", e == null ? null : e.getProgress());
                p.put("status", e == null ? "NOT_ENROLLED" : e.getStatus());
                p.put("overdue", e != null && !"COMPLETED".equals(e.getStatus()) && a.getDeadline() != null && a.getDeadline().isBefore(LocalDate.now()));
                progress.add(p);
            }
            row.put("progress", progress);
            members.add(row);
        }
        Map<String, Object> m = view(c);
        m.put("members", members);
        m.put("assignments", assignmentViews(assignments));
        m.put("announcements", announcementRepo.findTop20ByClassIdOrderByCreatedAtDesc(classId));
        return m;
    }

    /** 按邮箱 / 学号 / 手机号批量拉入学生;返回加入、已在班、未找到的明细 */
    @Transactional
    public Map<String, Object> addMembers(AuthUser viewer, Long classId, List<String> identifiers) {
        CourseClass c = owned(viewer, classId);
        List<String> added = new ArrayList<>();
        List<String> existed = new ArrayList<>();
        List<String> notFound = new ArrayList<>();
        List<String> notStudent = new ArrayList<>();
        for (String raw : identifiers) {
            String key = raw == null ? "" : raw.trim();
            if (key.isEmpty()) {
                continue;
            }
            User u = findStudent(key);
            if (u == null) {
                notFound.add(key);
                continue;
            }
            if (!"STUDENT".equals(u.getRole())) {
                notStudent.add(key);
                continue;
            }
            if (memberRepo.findByClassIdAndUserId(classId, u.getId()).isPresent()) {
                existed.add(key);
                continue;
            }
            join(c, u, false);
            added.add(u.getName());
        }
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("added", added);
        m.put("existed", existed);
        m.put("notFound", notFound);
        m.put("notStudent", notStudent);
        m.put("memberCount", memberRepo.countByClassId(classId));
        return m;
    }

    @Transactional
    public void removeMember(AuthUser viewer, Long classId, Long userId) {
        owned(viewer, classId);
        memberRepo.findByClassIdAndUserId(classId, userId).ifPresent(memberRepo::delete);
    }

    /** 布置项目:全班建报名、写统一截止、通知每个学生 */
    @Transactional
    public Map<String, Object> assign(AuthUser viewer, Long classId, Long projectId, LocalDate deadline, String note) {
        CourseClass c = owned(viewer, classId);
        Project p = projectRepository.findById(projectId).orElseThrow(() -> new BusinessException(404, "项目不存在"));
        if (assignmentRepo.findByClassIdAndProjectId(classId, projectId).isPresent()) {
            throw new BusinessException("该项目已布置给这个班");
        }
        ClassAssignment a = new ClassAssignment();
        a.setClassId(classId);
        a.setProjectId(projectId);
        a.setProjectTitle(p.getTitle());
        a.setDeadline(deadline);
        a.setNote(note == null ? null : cut(note.trim(), 300));
        assignmentRepo.save(a);
        int enrolled = 0;
        for (ClassMember m : memberRepo.findByClassIdOrderByJoinedAtAsc(classId)) {
            if (enrollForAssignment(m.getUserId(), p, a, c)) {
                enrolled++;
            }
        }
        statsService.refresh(projectId);
        c.setUpdatedAt(LocalDateTime.now());
        classRepo.save(c);
        Map<String, Object> m = assignmentView(a);
        m.put("newlyEnrolled", enrolled);
        return m;
    }

    @Transactional
    public Map<String, Object> updateAssignment(AuthUser viewer, Long classId, Long assignmentId, LocalDate deadline, String note) {
        owned(viewer, classId);
        ClassAssignment a = assignmentRepo.findById(assignmentId).filter(x -> x.getClassId().equals(classId))
                .orElseThrow(() -> new BusinessException(404, "作业不存在"));
        a.setDeadline(deadline);
        a.setNote(note == null ? null : cut(note.trim(), 300));
        assignmentRepo.save(a);
        // 截止日期同步到班里每个仍在进行的报名
        for (ClassMember m : memberRepo.findByClassIdOrderByJoinedAtAsc(classId)) {
            enrollmentRepository.findByUserIdAndProjectId(m.getUserId(), a.getProjectId()).ifPresent(e -> {
                if (!"COMPLETED".equals(e.getStatus())) {
                    e.setDeadline(deadline);
                    enrollmentRepository.save(e);
                }
            });
        }
        return assignmentView(a);
    }

    /** 撤销作业:只删布置记录,学生已有的报名与成果保留 */
    @Transactional
    public void removeAssignment(AuthUser viewer, Long classId, Long assignmentId) {
        owned(viewer, classId);
        assignmentRepo.findById(assignmentId).filter(x -> x.getClassId().equals(classId)).ifPresent(assignmentRepo::delete);
    }

    @Transactional
    public ClassAnnouncement announce(AuthUser viewer, Long classId, String title, String content, Long projectId) {
        CourseClass c = owned(viewer, classId);
        if (title == null || title.trim().isEmpty() || title.trim().length() > 100) {
            throw new BusinessException("公告标题需为 1~100 字");
        }
        ClassAnnouncement a = new ClassAnnouncement();
        a.setClassId(classId);
        a.setProjectId(projectId);
        a.setAuthorId(viewer.getId());
        a.setAuthorName(userRepository.findById(viewer.getId()).map(User::getName).orElse("教师"));
        a.setTitle(title.trim());
        a.setContent(content == null ? null : cut(content.trim(), 1000));
        announcementRepo.save(a);
        String body = (a.getContent() == null || a.getContent().isEmpty() ? "" : a.getContent() + " ") + "—— " + c.getName() + " · " + a.getAuthorName();
        for (ClassMember m : memberRepo.findByClassIdOrderByJoinedAtAsc(classId)) {
            notificationService.create(m.getUserId(), "project", "班级公告:" + a.getTitle(), body);
        }
        return a;
    }

    // ---------- 学生 ----------

    @Transactional
    public Map<String, Object> joinByCode(AuthUser student, String code) {
        String normalized = code == null ? "" : code.trim().toUpperCase();
        CourseClass c = classRepo.findByJoinCode(normalized).orElseThrow(() -> new BusinessException("加入码不正确"));
        if (!Boolean.TRUE.equals(c.getJoinEnabled()) || !"ACTIVE".equals(c.getStatus())) {
            throw new BusinessException("该班级已关闭自助加入,请联系老师");
        }
        User u = userRepository.findById(student.getId()).orElseThrow(() -> new BusinessException(401, "用户不存在"));
        if (memberRepo.findByClassIdAndUserId(c.getId(), u.getId()).isPresent()) {
            throw new BusinessException("你已经在这个班里了");
        }
        join(c, u, true);
        return view(c);
    }

    /** 我的班级:老师、加入码之外的信息、每个作业的我的进度、最近公告 */
    public List<Map<String, Object>> myClasses(Long userId) {
        List<Map<String, Object>> out = new ArrayList<>();
        for (ClassMember m : memberRepo.findByUserId(userId)) {
            CourseClass c = classRepo.findById(m.getClassId()).orElse(null);
            if (c == null) {
                continue;
            }
            Map<String, Object> v = new LinkedHashMap<>();
            v.put("id", c.getId());
            v.put("name", c.getName());
            v.put("description", c.getDescription());
            v.put("teacherName", c.getTeacherName());
            v.put("status", c.getStatus());
            v.put("memberCount", memberRepo.countByClassId(c.getId()));
            List<Map<String, Object>> assignments = new ArrayList<>();
            for (ClassAssignment a : assignmentRepo.findByClassIdOrderByCreatedAtDesc(c.getId())) {
                Map<String, Object> av = assignmentView(a);
                Enrollment e = enrollmentRepository.findByUserIdAndProjectId(userId, a.getProjectId()).orElse(null);
                av.put("progress", e == null ? 0 : e.getProgress());
                av.put("status", e == null ? "NOT_ENROLLED" : e.getStatus());
                av.put("overdue", e != null && !"COMPLETED".equals(e.getStatus()) && a.getDeadline() != null && a.getDeadline().isBefore(LocalDate.now()));
                assignments.add(av);
            }
            v.put("assignments", assignments);
            List<ClassAnnouncement> ann = announcementRepo.findTop20ByClassIdOrderByCreatedAtDesc(c.getId());
            v.put("announcements", ann.subList(0, Math.min(5, ann.size())));
            out.add(v);
        }
        return out;
    }

    // ---------- 内部 ----------

    private void join(CourseClass c, User u, boolean selfJoin) {
        ClassMember m = new ClassMember();
        m.setClassId(c.getId());
        m.setUserId(u.getId());
        memberRepo.save(m);
        int enrolled = 0;
        for (ClassAssignment a : assignmentRepo.findByClassIdOrderByCreatedAtDesc(c.getId())) {
            Project p = projectRepository.findById(a.getProjectId()).orElse(null);
            if (p != null && enrollForAssignment(u.getId(), p, a, c)) {
                enrolled++;
                statsService.refresh(p.getId());
            }
        }
        notificationService.create(u.getId(), "project", (selfJoin ? "已加入班级" : "老师把你加入了班级") + "「" + c.getName() + "」",
                "授课教师 " + c.getTeacherName() + (enrolled > 0 ? ",已自动为你报名 " + enrolled + " 个班级项目,请在「我的班级」查看截止日期。" : "。"));
    }

    /** 为班级作业建报名;已有报名则补班级与截止;返回是否新建 */
    private boolean enrollForAssignment(Long userId, Project p, ClassAssignment a, CourseClass c) {
        Optional<Enrollment> existing = enrollmentRepository.findByUserIdAndProjectId(userId, p.getId());
        if (existing.isPresent()) {
            Enrollment e = existing.get();
            if (e.getClassId() == null) {
                e.setClassId(c.getId());
            }
            if (a.getDeadline() != null && !"COMPLETED".equals(e.getStatus())) {
                e.setDeadline(a.getDeadline());
            }
            enrollmentRepository.save(e);
            return false;
        }
        Enrollment e = new Enrollment();
        e.setUserId(userId);
        e.setProjectId(p.getId());
        e.setProjectTitle(p.getTitle());
        e.setCurrentTask("阅读项目简介与前置知识");
        e.setDeadline(a.getDeadline());
        e.setClassId(c.getId());
        enrollmentRepository.save(e);
        activityService.record(userId, LearningActivity.ENROLL, p.getId(), "班级作业《" + p.getTitle() + "》");
        notificationService.create(userId, "project", "老师布置了新项目",
                "「" + c.getName() + "」布置了《" + p.getTitle() + "》" + (a.getDeadline() == null ? "" : ",截止 " + a.getDeadline())
                        + (a.getNote() == null || a.getNote().isEmpty() ? "" : "。" + a.getNote()));
        return true;
    }

    private User findStudent(String key) {
        if (key.contains("@")) {
            return userRepository.findByEmail(key).orElse(null);
        }
        if (key.matches("^1\\d{10}$")) {
            User byPhone = userRepository.findByPhone(key).orElse(null);
            if (byPhone != null) {
                return byPhone;
            }
        }
        return userRepository.findAll().stream().filter(u -> key.equals(u.getStudentNo())).findFirst().orElse(null);
    }

    private CourseClass owned(AuthUser viewer, Long classId) {
        CourseClass c = classRepo.findById(classId).orElseThrow(() -> new BusinessException(404, "班级不存在"));
        if (!viewer.isAdmin() && !viewer.getId().equals(c.getTeacherId())) {
            throw new BusinessException(403, "该班级不属于你");
        }
        return c;
    }

    private Map<String, Object> view(CourseClass c) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("id", c.getId());
        m.put("name", c.getName());
        m.put("description", c.getDescription());
        m.put("teacherId", c.getTeacherId());
        m.put("teacherName", c.getTeacherName());
        m.put("joinCode", c.getJoinCode());
        m.put("joinEnabled", c.getJoinEnabled());
        m.put("status", c.getStatus());
        m.put("memberCount", memberRepo.countByClassId(c.getId()));
        m.put("assignmentCount", assignmentRepo.countByClassId(c.getId()));
        m.put("createdAt", c.getCreatedAt());
        m.put("updatedAt", c.getUpdatedAt());
        return m;
    }

    private List<Map<String, Object>> assignmentViews(List<ClassAssignment> list) {
        List<Map<String, Object>> out = new ArrayList<>();
        for (ClassAssignment a : list) {
            out.add(assignmentView(a));
        }
        return out;
    }

    private Map<String, Object> assignmentView(ClassAssignment a) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("id", a.getId());
        m.put("classId", a.getClassId());
        m.put("projectId", a.getProjectId());
        m.put("projectTitle", a.getProjectTitle());
        m.put("deadline", a.getDeadline());
        m.put("note", a.getNote());
        m.put("createdAt", a.getCreatedAt());
        return m;
    }

    private String newCode() {
        for (int attempt = 0; attempt < 10; attempt++) {
            StringBuilder sb = new StringBuilder();
            for (int i = 0; i < 6; i++) {
                sb.append(CODE_ALPHABET.charAt(RANDOM.nextInt(CODE_ALPHABET.length())));
            }
            if (!classRepo.existsByJoinCode(sb.toString())) {
                return sb.toString();
            }
        }
        throw new BusinessException(500, "生成加入码失败,请重试");
    }

    private static String cut(String v, int max) {
        return v.length() <= max ? v : v.substring(0, max);
    }
}
