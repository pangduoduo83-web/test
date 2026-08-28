package com.example.ioedunew.controller;

import com.example.ioedunew.common.ApiResponse;
import com.example.ioedunew.config.AuthUser;
import com.example.ioedunew.dto.MiscDtos;
import com.example.ioedunew.entity.Discussion;
import com.example.ioedunew.entity.Enrollment;
import com.example.ioedunew.entity.Project;
import com.example.ioedunew.entity.Submission;
import com.example.ioedunew.service.ProjectService;
import com.example.ioedunew.service.SubmissionService;
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
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 项目中心接口:浏览、详情、报名、收藏、进度更新。
 */
@RestController
@RequestMapping("/api/projects")
public class ProjectController {

    private final ProjectService projectService;
    private final SubmissionService submissionService;

    public ProjectController(ProjectService projectService, SubmissionService submissionService) {
        this.projectService = projectService;
        this.submissionService = submissionService;
    }

    @GetMapping
    public ApiResponse<List<Project>> list(@RequestParam(required = false) String keyword,
                                           @RequestParam(required = false) String difficulty,
                                           @RequestParam(required = false) String sort) {
        return ApiResponse.ok(projectService.list(keyword, difficulty, sort));
    }

    @GetMapping("/{id}")
    public ApiResponse<Map<String, Object>> detail(@PathVariable Long id, HttpServletRequest request) {
        // 游客可匿名浏览详情,此时无 AuthUser,个人状态(报名/收藏)按未登录处理
        AuthUser user = auth(request);
        return ApiResponse.ok(projectService.detail(id, user == null ? null : user.getId()));
    }

    @PostMapping("/{id}/enroll")
    public ApiResponse<Enrollment> enroll(@PathVariable Long id, HttpServletRequest request) {
        return ApiResponse.ok(projectService.enroll(auth(request).getId(), id));
    }

    @PostMapping("/{id}/favorite")
    public ApiResponse<Map<String, Object>> favorite(@PathVariable Long id, HttpServletRequest request) {
        boolean favorited = projectService.toggleFavorite(auth(request).getId(), id);
        Map<String, Object> m = new HashMap<>();
        m.put("favorited", favorited);
        return ApiResponse.ok(m);
    }

    @PutMapping("/{id}/progress")
    public ApiResponse<Enrollment> progress(@PathVariable Long id,
                                            @Valid @RequestBody MiscDtos.ProgressUpdateRequest req,
                                            HttpServletRequest request) {
        return ApiResponse.ok(projectService.updateProgress(
                auth(request).getId(), id, req.getProgress(), req.getCurrentTask()));
    }

    @GetMapping("/{id}/discussions")
    public ApiResponse<List<Map<String, Object>>> discussions(@PathVariable Long id) {
        return ApiResponse.ok(projectService.discussions(id));
    }

    @PostMapping("/{id}/discussions")
    public ApiResponse<Discussion> postDiscussion(@PathVariable Long id,
                                                  @Valid @RequestBody MiscDtos.DiscussionPostRequest req,
                                                  HttpServletRequest request) {
        return ApiResponse.ok(projectService.postDiscussion(
                auth(request).getId(), id, req.getContent(), req.getParentId()));
    }

    @PostMapping("/{id}/submissions")
    public ApiResponse<Submission> submitWork(@PathVariable Long id,
                                              @Valid @RequestBody MiscDtos.SubmissionRequest req,
                                              HttpServletRequest request) {
        return ApiResponse.ok(submissionService.submit(auth(request).getId(), id, req));
    }

    @GetMapping("/{id}/submissions/mine")
    public ApiResponse<Submission> mySubmission(@PathVariable Long id, HttpServletRequest request) {
        return ApiResponse.ok(submissionService.mySubmission(auth(request).getId(), id));
    }

    /** 我的全部提交(含各考核项),用于分阶段考核视图 */
    @GetMapping("/{id}/submissions/mine-all")
    public ApiResponse<java.util.List<Submission>> mySubmissions(@PathVariable Long id,
                                                                 HttpServletRequest request) {
        return ApiResponse.ok(submissionService.mySubmissions(auth(request).getId(), id));
    }

    private AuthUser auth(HttpServletRequest request) {
        return (AuthUser) request.getAttribute(AuthUser.REQUEST_ATTR);
    }
}
