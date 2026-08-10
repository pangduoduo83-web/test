package com.example.ioedunew.controller;

import com.example.ioedunew.common.ApiResponse;
import com.example.ioedunew.config.AuthUser;
import com.example.ioedunew.dto.BorrowDtos;
import com.example.ioedunew.dto.MiscDtos;
import com.example.ioedunew.entity.BorrowRequest;
import com.example.ioedunew.entity.Equipment;
import com.example.ioedunew.entity.Project;
import com.example.ioedunew.entity.Submission;
import com.example.ioedunew.entity.User;
import com.example.ioedunew.repository.ProjectRepository;
import com.example.ioedunew.service.AdminService;
import com.example.ioedunew.service.AuthService;
import com.example.ioedunew.service.BorrowService;
import com.example.ioedunew.service.SubmissionService;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
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

    public AdminController(AdminService adminService,
                           BorrowService borrowService,
                           AuthService authService,
                           ProjectRepository projectRepository,
                           SubmissionService submissionService) {
        this.adminService = adminService;
        this.borrowService = borrowService;
        this.authService = authService;
        this.projectRepository = projectRepository;
        this.submissionService = submissionService;
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
    public ApiResponse<List<Submission>> submissions(@RequestParam(required = false) String status) {
        return ApiResponse.ok(submissionService.listAll(status));
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
    public ApiResponse<List<User>> users() {
        return ApiResponse.ok(adminService.listUsers());
    }

    @PutMapping("/users/{id}")
    public ApiResponse<User> updateUser(@PathVariable Long id,
                                        @RequestBody MiscDtos.UserAdminUpdateRequest req) {
        return ApiResponse.ok(adminService.updateUser(id, req));
    }

    private String adminName(HttpServletRequest request) {
        AuthUser auth = (AuthUser) request.getAttribute(AuthUser.REQUEST_ATTR);
        return authService.me(auth.getId()).getName();
    }
}
