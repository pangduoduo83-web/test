package com.example.ioedunew.controller;

import com.example.ioedunew.common.ApiResponse;
import com.example.ioedunew.service.AiServiceTestService;
import org.springframework.web.bind.annotation.*;
import java.util.Map;

/** Admin-only namespace; draft tests never save settings. */
@RestController
@RequestMapping("/api/admin/ai-settings/test")
public class AiServiceTestController {
    private final AiServiceTestService tests;

    public AiServiceTestController(AiServiceTestService tests) { this.tests = tests; }

    @PostMapping("/{service}")
    public ApiResponse<Map<String, Object>> test(@PathVariable String service,
                                                @RequestBody Map<String, Object> draft) {
        return ApiResponse.ok(tests.test(service, draft));
    }
}
