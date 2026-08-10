package com.example.ioedunew.controller;

import com.example.ioedunew.common.ApiResponse;
import com.example.ioedunew.config.AuthUser;
import com.example.ioedunew.dto.MiscDtos;
import com.example.ioedunew.service.SkillService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import javax.servlet.http.HttpServletRequest;
import javax.validation.Valid;
import java.util.Map;

/**
 * 技能评估接口:查询技能画像、提交测评结果。
 */
@RestController
@RequestMapping("/api/skills")
public class SkillController {

    private final SkillService skillService;

    public SkillController(SkillService skillService) {
        this.skillService = skillService;
    }

    @GetMapping
    public ApiResponse<Map<String, Object>> summary(HttpServletRequest request) {
        return ApiResponse.ok(skillService.summary(auth(request).getId()));
    }

    @PostMapping("/assess")
    public ApiResponse<Map<String, Object>> assess(@Valid @RequestBody MiscDtos.SkillAssessRequest req,
                                                   HttpServletRequest request) {
        return ApiResponse.ok(skillService.assess(auth(request).getId(), req.getScores()));
    }

    private AuthUser auth(HttpServletRequest request) {
        return (AuthUser) request.getAttribute(AuthUser.REQUEST_ATTR);
    }
}
