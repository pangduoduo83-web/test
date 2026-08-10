package com.example.ioedunew.controller;

import com.example.ioedunew.common.ApiResponse;
import com.example.ioedunew.config.AuthUser;
import com.example.ioedunew.dto.TeacherDtos;
import com.example.ioedunew.entity.Project;
import com.example.ioedunew.service.TeacherService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestAttribute;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;

/**
 * 教师端接口:/api/teacher 下由拦截器保证 TEACHER 或 ADMIN 角色。
 */
@RestController
@RequestMapping("/api/teacher")
public class TeacherController {

    private final TeacherService teacherService;

    public TeacherController(TeacherService teacherService) {
        this.teacherService = teacherService;
    }

    @GetMapping("/stats")
    public ApiResponse<Map<String, Object>> stats(@RequestAttribute(AuthUser.REQUEST_ATTR) AuthUser user) {
        return ApiResponse.ok(teacherService.stats(user.getId(), user.isAdmin()));
    }

    @GetMapping("/projects")
    public ApiResponse<List<Project>> myProjects(@RequestAttribute(AuthUser.REQUEST_ATTR) AuthUser user) {
        return ApiResponse.ok(teacherService.myProjects(user.getId(), user.isAdmin()));
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
}
