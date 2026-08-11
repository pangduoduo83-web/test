package com.example.ioedunew.controller;

import com.example.ioedunew.common.ApiResponse;
import com.example.ioedunew.config.AuthUser;
import com.example.ioedunew.dto.AdminDtos;
import com.example.ioedunew.dto.BorrowDtos;
import com.example.ioedunew.dto.MiscDtos;
import com.example.ioedunew.entity.BorrowRequest;
import com.example.ioedunew.entity.Enrollment;
import com.example.ioedunew.entity.Equipment;
import com.example.ioedunew.entity.Notification;
import com.example.ioedunew.entity.Project;
import com.example.ioedunew.entity.Submission;
import com.example.ioedunew.entity.User;
import com.example.ioedunew.repository.ProjectRepository;
import com.example.ioedunew.service.AdminService;
import com.example.ioedunew.service.AuthService;
import com.example.ioedunew.service.BorrowService;
import com.example.ioedunew.service.NotificationService;
import com.example.ioedunew.service.SubmissionService;
import com.example.ioedunew.service.TeacherService;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestAttribute;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import javax.servlet.http.HttpServletRequest;
import javax.validation.Valid;
import java.util.List;
import java.util.Map;

/**
 * 管理端接口:数据看板、设备/项目 CRUD、借阅审批、用户管理。
 * 权限:整个 /api/admin 前缀已由拦截器限定 ADMIN 角色。
 */
@RestController
@RequestMapping("/api/admin")
public class AdminController {

    private final AdminService adminService;
    private final BorrowService borrowService;
    private final AuthService authService;
    private final ProjectRepository projectRepository;
    private final SubmissionService submissionService;
    private final TeacherService teacherService;
    private final NotificationService notificationService;

    public AdminController(AdminService adminService,
                           BorrowService borrowService,
                           AuthService authService,
                           ProjectRepository projectRepository,
                           SubmissionService submissionService,
                           TeacherService teacherService,
                           NotificationService notificationService) {
        this.adminService = adminService;
        this.borrowService = borrowService;
        this.authService = authService;
        this.projectRepository = projectRepository;
        this.submissionService = submissionService;
        this.teacherService = teacherService;
        this.notificationService = notificationService;
    }

    // ---------- 数据看板 ----------

    @GetMapping("/stats")
    public ApiResponse<Map<String, Object>> stats() {
        return ApiResponse.ok(adminService.stats());
    }

    @GetMapping("/trends")
    public ApiResponse<Map<String, Object>> trends() {
        return ApiResponse.ok(adminService.trends());
    }

    // ---------- 成果评审 ----------

    @GetMapping("/submissions")
    public ApiResponse<List<Submission>> submissions(@RequestParam(required = false) String status,
                                                      @RequestParam(required = false) Long userId,
                                                      @RequestParam(required = false) Long projectId) {
        return ApiResponse.ok(submissionService.listAll(status, userId, projectId));
    }

    @PostMapping("/submissions/{id}/grade")
    public ApiResponse<Submission> grade(@PathVariable Long id,
                                         @Valid @RequestBody MiscDtos.GradeRequest req,
                                         HttpServletRequest request) {
        return ApiResponse.ok(submissionService.grade(id, req, adminName(request)));
    }

    // ---------- 设备管理 ----------

    @PostMapping("/equipment")
    public ApiResponse<Equipment> createEquipment(@RequestBody Equipment equipment) {
        equipment.setId(null);
        return ApiResponse.ok(adminService.saveEquipment(equipment));
    }

    @PutMapping("/equipment/{id}")
    public ApiResponse<Equipment> updateEquipment(@PathVariable Long id, @RequestBody Equipment equipment) {
        equipment.setId(id);
        return ApiResponse.ok(adminService.saveEquipment(equipment));
    }

    @DeleteMapping("/equipment/{id}")
    public ApiResponse<Void> deleteEquipment(@PathVariable Long id) {
        adminService.deleteEquipment(id);
        return ApiResponse.ok();
    }

    // ---------- 项目管理 ----------

    @GetMapping("/projects")
    public ApiResponse<List<Project>> allProjects() {
        return ApiResponse.ok(projectRepository.findAll());
    }

    @PostMapping("/projects")
    public ApiResponse<Project> createProject(@RequestBody Project project) {
        project.setId(null);
        return ApiResponse.ok(adminService.saveProject(project));
    }

    @PutMapping("/projects/{id}")
    public ApiResponse<Project> updateProject(@PathVariable Long id, @RequestBody Project project) {
        project.setId(id);
        return ApiResponse.ok(adminService.saveProject(project));
    }

    @DeleteMapping("/projects/{id}")
    public ApiResponse<Void> deleteProject(@PathVariable Long id) {
        adminService.deleteProject(id);
        return ApiResponse.ok();
    }

    @GetMapping("/projects/{id}/students")
    public ApiResponse<List<Map<String, Object>>> projectStudents(
            @PathVariable Long id,
            @RequestAttribute(AuthUser.REQUEST_ATTR) AuthUser user) {
        return ApiResponse.ok(teacherService.projectStudents(user.getId(), true, id));
    }

    // ---------- 报名与进度 ----------

    @GetMapping("/enrollments")
    public ApiResponse<List<Enrollment>> enrollments(@RequestParam(required = false) Long projectId,
                                                     @RequestParam(required = false) Long userId,
                                                     @RequestParam(required = false) String status) {
        return ApiResponse.ok(adminService.listEnrollments(projectId, userId, status));
    }

    @PostMapping("/enrollments")
    public ApiResponse<Enrollment> createEnrollment(
            @Valid @RequestBody AdminDtos.EnrollmentCreateRequest req) {
        return ApiResponse.ok(adminService.createEnrollment(req));
    }

    @PutMapping("/enrollments/{id}")
    public ApiResponse<Enrollment> updateEnrollment(
            @PathVariable Long id,
            @Valid @RequestBody AdminDtos.EnrollmentUpdateRequest req) {
        return ApiResponse.ok(adminService.updateEnrollment(id, req));
    }

    @DeleteMapping("/enrollments/{id}")
    public ApiResponse<Void> deleteEnrollment(@PathVariable Long id) {
        adminService.deleteEnrollment(id);
        return ApiResponse.ok();
    }

    // ---------- 借阅审批 ----------

    @GetMapping("/borrows")
    public ApiResponse<List<BorrowRequest>> borrows(@RequestParam(required = false) String status) {
        return ApiResponse.ok(borrowService.listAll(status));
    }

    @PostMapping("/borrows/{id}/decide")
    public ApiResponse<BorrowRequest> decide(@PathVariable Long id,
                                             @Valid @RequestBody BorrowDtos.DecisionRequest req,
                                             HttpServletRequest request) {
        return ApiResponse.ok(borrowService.decide(id, req, adminName(request)));
    }

    @PostMapping("/borrows/{id}/confirm-return")
    public ApiResponse<BorrowRequest> confirmReturn(@PathVariable Long id, HttpServletRequest request) {
        return ApiResponse.ok(borrowService.confirmReturn(id, adminName(request)));
    }

    // ---------- 用户管理 ----------

    @GetMapping("/users")
    public ApiResponse<List<User>> users(@RequestParam(required = false) String keyword,
                                         @RequestParam(required = false) String role,
                                         @RequestParam(required = false) Boolean enabled) {
        return ApiResponse.ok(adminService.listUsers(keyword, role, enabled));
    }

    @GetMapping("/users/{id}")
    public ApiResponse<User> user(@PathVariable Long id) {
        return ApiResponse.ok(adminService.getUser(id));
    }

    @PostMapping("/users")
    public ApiResponse<User> createUser(@Valid @RequestBody AdminDtos.UserCreateRequest req) {
        return ApiResponse.ok(adminService.createUser(req));
    }

    @PutMapping("/users/{id}")
    public ApiResponse<User> updateUser(@PathVariable Long id,
                                        @Valid @RequestBody AdminDtos.UserUpdateRequest req) {
        return ApiResponse.ok(adminService.updateUser(id, req));
    }

    @PostMapping("/users/{id}/reset-password")
    public ApiResponse<Void> resetPassword(@PathVariable Long id,
                                           @Valid @RequestBody AdminDtos.ResetPasswordRequest req) {
        adminService.resetPassword(id, req.getPassword());
        return ApiResponse.ok();
    }

    @DeleteMapping("/users/{id}")
    public ApiResponse<Void> deleteUser(@PathVariable Long id,
                                        @RequestAttribute(AuthUser.REQUEST_ATTR) AuthUser user) {
        adminService.deleteUser(id, user.getId());
        return ApiResponse.ok();
    }

    // ---------- 通知管理 ----------

    @GetMapping("/notifications")
    public ApiResponse<List<Notification>> notifications(@RequestParam(required = false) Long userId,
                                                         @RequestParam(required = false) String type,
                                                         @RequestParam(name = "read", required = false) Boolean read) {
        return ApiResponse.ok(notificationService.adminList(userId, type, read));
    }

    @PostMapping("/notifications")
    public ApiResponse<Integer> sendNotification(
            @Valid @RequestBody AdminDtos.NotificationCreateRequest req) {
        return ApiResponse.ok(notificationService.adminSend(req));
    }

    @DeleteMapping("/notifications/{id}")
    public ApiResponse<Void> deleteNotification(@PathVariable Long id) {
        notificationService.adminDelete(id);
        return ApiResponse.ok();
    }

    private String adminName(HttpServletRequest request) {
        AuthUser auth = (AuthUser) request.getAttribute(AuthUser.REQUEST_ATTR);
        return authService.me(auth.getId()).getName();
    }
}
