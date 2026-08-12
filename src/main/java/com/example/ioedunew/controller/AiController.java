package com.example.ioedunew.controller;

import com.example.ioedunew.common.ApiResponse;
import com.example.ioedunew.config.AuthUser;
import com.example.ioedunew.service.AiPlanService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestAttribute;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

/**
 * AI 能力接口(需登录):当前提供 AI 学习规划师。
 * 模型密钥只存在服务端,小程序与网页都经由此代理访问。
 */
@RestController
@RequestMapping("/api/ai")
public class AiController {

    private final AiPlanService aiPlanService;

    public AiController(AiPlanService aiPlanService) {
        this.aiPlanService = aiPlanService;
    }

    /** 读取缓存的学习计划,没有则返回 null */
    @GetMapping("/learning-plan")
    public ApiResponse<Map<String, Object>> plan(@RequestAttribute(AuthUser.REQUEST_ATTR) AuthUser user) {
        return ApiResponse.ok(aiPlanService.getCached(user.getId()));
    }

    /** 生成学习计划,body 可选 { goal, weeklyHours } */
    @PostMapping("/learning-plan/generate")
    public ApiResponse<Map<String, Object>> generate(
            @RequestAttribute(AuthUser.REQUEST_ATTR) AuthUser user,
            @RequestBody(required = false) Map<String, Object> body) {
        String goal = null;
        Integer weeklyHours = null;
        if (body != null) {
            Object g = body.get("goal");
            if (g instanceof String && !((String) g).trim().isEmpty()) {
                String t = ((String) g).trim();
                goal = t.length() > 200 ? t.substring(0, 200) : t;
            }
            Object w = body.get("weeklyHours");
            if (w instanceof Number) {
                weeklyHours = ((Number) w).intValue();
            }
        }
        return ApiResponse.ok(aiPlanService.generate(user.getId(), goal, weeklyHours));
    }
}
