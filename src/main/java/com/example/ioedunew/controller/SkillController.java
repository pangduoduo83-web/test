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
    private final com.example.ioedunew.service.SkillQuizService quizService;

    public SkillController(SkillService skillService, com.example.ioedunew.service.SkillQuizService quizService) {
        this.skillService = skillService;
        this.quizService = quizService;
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

    /** AI 出题,body: { skillName } */
    @PostMapping("/quiz/start")
    public ApiResponse<Map<String, Object>> quizStart(@RequestBody Map<String, String> body, HttpServletRequest request) {
        return ApiResponse.ok(quizService.start(auth(request), body.get("skillName")));
    }

    /** 提交答案,body: { quizId, answers: [选项下标...] } */
    @PostMapping("/quiz/submit")
    public ApiResponse<Map<String, Object>> quizSubmit(@RequestBody Map<String, Object> body, HttpServletRequest request) {
        java.util.List<Integer> answers = new java.util.ArrayList<>();
        Object raw = body.get("answers");
        if (raw instanceof java.util.List) {
            for (Object o : (java.util.List<?>) raw) {
                answers.add(o == null ? null : ((Number) o).intValue());
            }
        }
        return ApiResponse.ok(quizService.submit(auth(request), String.valueOf(body.get("quizId")), answers));
    }

    private AuthUser auth(HttpServletRequest request) {
        return (AuthUser) request.getAttribute(AuthUser.REQUEST_ATTR);
    }
}
