package com.example.ioedunew.controller;

import com.example.ioedunew.ai.skill.AiRun;
import com.example.ioedunew.ai.skill.AiRunRepository;
import com.example.ioedunew.ai.skill.AiToolInvocation;
import com.example.ioedunew.ai.skill.AiToolInvocationRepository;
import com.example.ioedunew.ai.skill.AiUsageService;
import com.example.ioedunew.ai.skill.AiSkillService;
import com.example.ioedunew.ai.skill.ToolPolicyService;
import com.example.ioedunew.common.ApiResponse;
import com.example.ioedunew.config.AuthUser;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestAttribute;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** 管理端 AI 中心:本站 SKILL 管理、工具策略、运行审计与用量 */
@RestController
@RequestMapping("/api/admin/ai")
public class AdminAiController {

    private final AiSkillService skillService;
    private final ToolPolicyService policyService;
    private final AiRunRepository runRepo;
    private final AiToolInvocationRepository invocationRepo;
    private final AiUsageService usageService;

    public AdminAiController(AiSkillService skillService, ToolPolicyService policyService, AiRunRepository runRepo,
                             AiToolInvocationRepository invocationRepo, AiUsageService usageService) {
        this.skillService = skillService;
        this.policyService = policyService;
        this.runRepo = runRepo;
        this.invocationRepo = invocationRepo;
        this.usageService = usageService;
    }

    @GetMapping("/skills")
    public ApiResponse<List<Map<String, Object>>> skills(@RequestAttribute(AuthUser.REQUEST_ATTR) AuthUser admin) {
        return ApiResponse.ok(skillService.allStored(admin));
    }

    /** 个人 SKILL 提升为本站共享 */
    @PostMapping("/skills/{id}/promote")
    public ApiResponse<Map<String, Object>> promote(@PathVariable Long id,
                                                    @RequestAttribute(AuthUser.REQUEST_ATTR) AuthUser admin) {
        return ApiResponse.ok(skillService.promote(id, admin));
    }

    @GetMapping("/tools")
    public ApiResponse<List<Map<String, Object>>> tools() {
        return ApiResponse.ok(policyService.listForAdmin());
    }

    /** body: { enabled?, minRole?, requiresConfirmation?, dailyLimit? } */
    @PutMapping("/tools/{name}")
    public ApiResponse<Map<String, Object>> updateTool(@PathVariable String name, @RequestBody Map<String, Object> body) {
        return ApiResponse.ok(policyService.update(name, body));
    }

    @GetMapping("/runs")
    public ApiResponse<Map<String, Object>> runs(@RequestParam(defaultValue = "0") int page,
                                                 @RequestParam(defaultValue = "20") int size,
                                                 @RequestParam(required = false) Long userId) {
        Page<AiRun> p = userId == null
                ? runRepo.findAllByOrderByIdDesc(PageRequest.of(page, Math.min(size, 100)))
                : runRepo.findByUserIdOrderByIdDesc(userId, PageRequest.of(page, Math.min(size, 100)));
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("items", p.getContent());
        m.put("total", p.getTotalElements());
        m.put("page", page);
        m.put("size", size);
        return ApiResponse.ok(m);
    }

    @GetMapping("/runs/{id}/tools")
    public ApiResponse<List<AiToolInvocation>> runTools(@PathVariable Long id) {
        return ApiResponse.ok(invocationRepo.findByRunIdOrderByIdAsc(id));
    }

    @GetMapping("/usage")
    public ApiResponse<Map<String, Object>> usage(@RequestParam(defaultValue = "14") int days) {
        return ApiResponse.ok(usageService.summary(Math.min(Math.max(days, 1), 90)));
    }
}
