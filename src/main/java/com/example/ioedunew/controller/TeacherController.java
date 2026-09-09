package com.example.ioedunew.controller;

import com.example.ioedunew.common.ApiResponse;
import com.example.ioedunew.config.AuthUser;
import com.example.ioedunew.dto.MiscDtos;
import com.example.ioedunew.dto.TeacherDtos;
import com.example.ioedunew.entity.ClassAnnouncement;
import com.example.ioedunew.entity.Project;
import com.example.ioedunew.entity.Submission;
import com.example.ioedunew.repository.UserRepository;
import com.example.ioedunew.service.AiReviewService;
import com.example.ioedunew.service.ClassService;
import com.example.ioedunew.service.SkillDimensionService;
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

import javax.validation.Valid;
import java.util.List;
import java.util.Map;

/**
 * 教师端接口:/api/teacher 下由拦截器保证 TEACHER 或 ADMIN 角色。
 * 教师只能操作自己指导(mentorId)的项目及其学生成果;管理员在此处视为拥有全部项目。
 */
@RestController
@RequestMapping("/api/teacher")
public class TeacherController {

    private final TeacherService teacherService;
    private final SubmissionService submissionService;
    private final AiReviewService aiReviewService;
    private final SkillDimensionService skillDimensionService;
    private final ClassService classService;
    private final UserRepository userRepository;

    public TeacherController(TeacherService teacherService, SubmissionService submissionService,
                             AiReviewService aiReviewService, SkillDimensionService skillDimensionService,
                             ClassService classService, UserRepository userRepository) {
        this.teacherService = teacherService;
        this.submissionService = submissionService;
        this.aiReviewService = aiReviewService;
        this.skillDimensionService = skillDimensionService;
        this.classService = classService;
        this.userRepository = userRepository;
    }

    @GetMapping("/stats")
    public ApiResponse<Map<String, Object>> stats(@RequestAttribute(AuthUser.REQUEST_ATTR) AuthUser user) {
        return ApiResponse.ok(teacherService.stats(user.getId(), user.isAdmin()));
    }

    @GetMapping("/projects")
    public ApiResponse<List<Project>> myProjects(@RequestAttribute(AuthUser.REQUEST_ATTR) AuthUser user) {
        return ApiResponse.ok(teacherService.myProjects(user.getId(), user.isAdmin()));
    }

    /** 项目编辑表单里的技能维度选项(与管理端同一份) */
    @GetMapping("/skill-dimensions")
    public ApiResponse<List<Map<String, Object>>> skillDimensions() {
        return ApiResponse.ok(skillDimensionService.listForAdmin());
    }

    /** 教师新建项目(自动成为指导教师) */
    @PostMapping("/projects")
    public ApiResponse<Project> createProject(@RequestBody Project project,
                                              @RequestAttribute(AuthUser.REQUEST_ATTR) AuthUser user) {
        return ApiResponse.ok(teacherService.createProject(user.getId(), project));
    }

    /** 教师编辑自己项目的全部教学内容(讲师归属与统计字段由服务端保留) */
    @PutMapping("/projects/{id}")
    public ApiResponse<Project> updateProject(@PathVariable Long id, @RequestBody Project project,
                                              @RequestAttribute(AuthUser.REQUEST_ATTR) AuthUser user) {
        return ApiResponse.ok(teacherService.updateProject(user.getId(), user.isAdmin(), id, project));
    }

    @PutMapping("/projects/{id}/resources")
    public ApiResponse<Project> updateResources(@PathVariable Long id,
                                                @RequestBody TeacherDtos.ResourcesUpdateRequest req,
                                                @RequestAttribute(AuthUser.REQUEST_ATTR) AuthUser user) {
        return ApiResponse.ok(teacherService.updateResources(user.getId(), user.isAdmin(), id, req.getResources()));
    }

    @PutMapping("/projects/{id}/cover")
    public ApiResponse<Project> updateCover(@PathVariable Long id,
                                            @RequestBody TeacherDtos.CoverUpdateRequest req,
                                            @RequestAttribute(AuthUser.REQUEST_ATTR) AuthUser user) {
        return ApiResponse.ok(teacherService.updateCover(user.getId(), user.isAdmin(), id, req.getCoverUrl()));
    }

    @GetMapping("/projects/{id}/students")
    public ApiResponse<List<Map<String, Object>>> projectStudents(@PathVariable Long id,
                                                                  @RequestAttribute(AuthUser.REQUEST_ATTR) AuthUser user) {
        return ApiResponse.ok(teacherService.projectStudents(user.getId(), user.isAdmin(), id));
    }

    // ---------- 成果评审(仅自己指导的项目) ----------

    @GetMapping("/submissions")
    public ApiResponse<List<Submission>> submissions(@RequestParam(required = false) String status,
                                                      @RequestParam(required = false) Long projectId,
                                                      @RequestAttribute(AuthUser.REQUEST_ATTR) AuthUser user) {
        return ApiResponse.ok(submissionService.listForMentor(user.isAdmin() ? null : user.getId(), status, projectId));
    }

    @PostMapping("/submissions/{id}/ai-review")
    public ApiResponse<Map<String, Object>> aiReview(@PathVariable Long id,
                                                     @RequestAttribute(AuthUser.REQUEST_ATTR) AuthUser user) {
        if (!user.isAdmin()) {
            submissionService.requireMentor(id, user.getId());
        }
        return ApiResponse.ok(aiReviewService.review(id));
    }

    @PostMapping("/submissions/{id}/grade")
    public ApiResponse<Submission> grade(@PathVariable Long id, @Valid @RequestBody MiscDtos.GradeRequest req,
                                         @RequestAttribute(AuthUser.REQUEST_ATTR) AuthUser user) {
        if (!user.isAdmin()) {
            submissionService.requireMentor(id, user.getId());
        }
        return ApiResponse.ok(submissionService.grade(id, req, actorName(user)));
    }

    /** 退回修改,body: { feedback } */
    @PostMapping("/submissions/{id}/return")
    public ApiResponse<Submission> returnSubmission(@PathVariable Long id, @RequestBody Map<String, String> body,
                                                    @RequestAttribute(AuthUser.REQUEST_ATTR) AuthUser user) {
        if (!user.isAdmin()) {
            submissionService.requireMentor(id, user.getId());
        }
        return ApiResponse.ok(submissionService.returnForRevision(id, body.get("feedback"), actorName(user)));
    }

    // ---------- 公告与掉队名单 ----------

    /** 给项目全部报名学生发公告,body: { title, content } */
    @PostMapping("/projects/{id}/announce")
    public ApiResponse<Map<String, Object>> announce(@PathVariable Long id, @RequestBody Map<String, String> body,
                                                     @RequestAttribute(AuthUser.REQUEST_ATTR) AuthUser user) {
        int n = teacherService.announceToProject(user.getId(), user.isAdmin(), id, body.get("title"), body.get("content"));
        Map<String, Object> m = new java.util.LinkedHashMap<>();
        m.put("notified", n);
        return ApiResponse.ok(m);
    }

    @GetMapping("/at-risk")
    public ApiResponse<List<Map<String, Object>>> atRisk(@RequestAttribute(AuthUser.REQUEST_ATTR) AuthUser user) {
        return ApiResponse.ok(teacherService.atRisk(user.getId(), user.isAdmin()));
    }

    /** 定向提醒一名学生,body: { message? } */
    @PostMapping("/projects/{id}/remind/{studentId}")
    public ApiResponse<Void> remind(@PathVariable Long id, @PathVariable Long studentId,
                                    @RequestBody(required = false) Map<String, String> body,
                                    @RequestAttribute(AuthUser.REQUEST_ATTR) AuthUser user) {
        teacherService.remindStudent(user.getId(), user.isAdmin(), id, studentId, body == null ? null : body.get("message"));
        return ApiResponse.ok();
    }

    // ---------- 课程班 ----------

    @GetMapping("/classes")
    public ApiResponse<List<Map<String, Object>>> classes(@RequestAttribute(AuthUser.REQUEST_ATTR) AuthUser user) {
        return ApiResponse.ok(classService.list(user));
    }

    /** body: { name, description?, teacherId?(仅管理员) } */
    @PostMapping("/classes")
    public ApiResponse<Map<String, Object>> createClass(@RequestBody Map<String, Object> body,
                                                        @RequestAttribute(AuthUser.REQUEST_ATTR) AuthUser user) {
        Long teacherId = body.get("teacherId") == null ? null : Long.valueOf(String.valueOf(body.get("teacherId")));
        return ApiResponse.ok(classService.create(user, str(body.get("name")), str(body.get("description")), teacherId));
    }

    @GetMapping("/classes/{id}")
    public ApiResponse<Map<String, Object>> classDetail(@PathVariable Long id, @RequestAttribute(AuthUser.REQUEST_ATTR) AuthUser user) {
        return ApiResponse.ok(classService.detail(user, id));
    }

    /** body: { name?, description?, joinEnabled?, status?, regenerateCode?, teacherId?(仅管理员) } */
    @PutMapping("/classes/{id}")
    public ApiResponse<Map<String, Object>> updateClass(@PathVariable Long id, @RequestBody Map<String, Object> body,
                                                        @RequestAttribute(AuthUser.REQUEST_ATTR) AuthUser user) {
        return ApiResponse.ok(classService.update(user, id, body));
    }

    @DeleteMapping("/classes/{id}")
    public ApiResponse<Void> deleteClass(@PathVariable Long id, @RequestAttribute(AuthUser.REQUEST_ATTR) AuthUser user) {
        classService.delete(user, id);
        return ApiResponse.ok();
    }

    /** body: { identifiers: ["学号或邮箱或手机号", ...] } */
    @PostMapping("/classes/{id}/members")
    public ApiResponse<Map<String, Object>> addMembers(@PathVariable Long id, @RequestBody Map<String, Object> body,
                                                       @RequestAttribute(AuthUser.REQUEST_ATTR) AuthUser user) {
        List<String> ids = new java.util.ArrayList<>();
        Object raw = body.get("identifiers");
        if (raw instanceof List) {
            for (Object o : (List<?>) raw) {
                ids.add(String.valueOf(o));
            }
        }
        return ApiResponse.ok(classService.addMembers(user, id, ids));
    }

    @DeleteMapping("/classes/{id}/members/{userId}")
    public ApiResponse<Void> removeMember(@PathVariable Long id, @PathVariable Long userId,
                                          @RequestAttribute(AuthUser.REQUEST_ATTR) AuthUser user) {
        classService.removeMember(user, id, userId);
        return ApiResponse.ok();
    }

    /** body: { projectId, deadline?(yyyy-MM-dd), note? } */
    @PostMapping("/classes/{id}/assignments")
    public ApiResponse<Map<String, Object>> assign(@PathVariable Long id, @RequestBody Map<String, Object> body,
                                                   @RequestAttribute(AuthUser.REQUEST_ATTR) AuthUser user) {
        return ApiResponse.ok(classService.assign(user, id, Long.valueOf(String.valueOf(body.get("projectId"))),
                date(body.get("deadline")), str(body.get("note"))));
    }

    @PutMapping("/classes/{id}/assignments/{assignmentId}")
    public ApiResponse<Map<String, Object>> updateAssignment(@PathVariable Long id, @PathVariable Long assignmentId,
                                                             @RequestBody Map<String, Object> body,
                                                             @RequestAttribute(AuthUser.REQUEST_ATTR) AuthUser user) {
        return ApiResponse.ok(classService.updateAssignment(user, id, assignmentId, date(body.get("deadline")), str(body.get("note"))));
    }

    @DeleteMapping("/classes/{id}/assignments/{assignmentId}")
    public ApiResponse<Void> removeAssignment(@PathVariable Long id, @PathVariable Long assignmentId,
                                              @RequestAttribute(AuthUser.REQUEST_ATTR) AuthUser user) {
        classService.removeAssignment(user, id, assignmentId);
        return ApiResponse.ok();
    }

    /** body: { title, content?, projectId? } */
    @PostMapping("/classes/{id}/announcements")
    public ApiResponse<ClassAnnouncement> classAnnounce(@PathVariable Long id, @RequestBody Map<String, Object> body,
                                                        @RequestAttribute(AuthUser.REQUEST_ATTR) AuthUser user) {
        Long projectId = body.get("projectId") == null ? null : Long.valueOf(String.valueOf(body.get("projectId")));
        return ApiResponse.ok(classService.announce(user, id, str(body.get("title")), str(body.get("content")), projectId));
    }

    private String actorName(AuthUser user) {
        return userRepository.findById(user.getId()).map(u -> u.getName()).orElse("教师");
    }

    private static String str(Object o) {
        return o == null ? null : String.valueOf(o);
    }

    private static java.time.LocalDate date(Object o) {
        if (o == null || String.valueOf(o).trim().isEmpty()) {
            return null;
        }
        try {
            return java.time.LocalDate.parse(String.valueOf(o).trim());
        } catch (Exception e) {
            throw new com.example.ioedunew.common.BusinessException("日期格式应为 yyyy-MM-dd");
        }
    }
}
