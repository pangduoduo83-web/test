package com.example.ioedunew.controller;

import com.example.ioedunew.common.ApiResponse;
import com.example.ioedunew.config.AuthUser;
import com.example.ioedunew.service.ClassService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestAttribute;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;

/** 学生端班级接口:我的班级、凭加入码加入 */
@RestController
@RequestMapping("/api/classes")
public class ClassController {

    private final ClassService classService;

    public ClassController(ClassService classService) {
        this.classService = classService;
    }

    @GetMapping("/mine")
    public ApiResponse<List<Map<String, Object>>> mine(@RequestAttribute(AuthUser.REQUEST_ATTR) AuthUser user) {
        return ApiResponse.ok(classService.myClasses(user.getId()));
    }

    /** body: { code } */
    @PostMapping("/join")
    public ApiResponse<Map<String, Object>> join(@RequestBody Map<String, String> body,
                                                 @RequestAttribute(AuthUser.REQUEST_ATTR) AuthUser user) {
        return ApiResponse.ok(classService.joinByCode(user, body.get("code")));
    }
}
