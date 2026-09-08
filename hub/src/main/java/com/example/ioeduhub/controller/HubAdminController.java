package com.example.ioeduhub.controller;

import com.example.ioeduhub.common.ApiResponse;
import com.example.ioeduhub.config.HubAuthFilter;
import com.example.ioeduhub.service.HubAdminService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestAttribute;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/** 平台管理员接口(登录后 JWT 鉴权) */
@RestController
@RequestMapping("/api/hub-admin")
public class HubAdminController {

    private final HubAdminService adminService;

    public HubAdminController(HubAdminService adminService) {
        this.adminService = adminService;
    }

    @PostMapping("/login")
    public ApiResponse<Map<String, Object>> login(@RequestBody Map<String, String> body) {
        return ApiResponse.ok(adminService.login(body.get("username"), body.get("password")));
    }

    @PostMapping("/password")
    public ApiResponse<Void> changePassword(@RequestAttribute(HubAuthFilter.ATTR_ADMIN) String admin,
                                            @RequestBody Map<String, String> body) {
        adminService.changePassword(admin, body.get("oldPassword"), body.get("newPassword"));
        return ApiResponse.ok();
    }

    @GetMapping("/stats")
    public ApiResponse<Map<String, Object>> stats() {
        return ApiResponse.ok(adminService.stats());
    }

    @GetMapping("/items")
    public ApiResponse<List<Map<String, Object>>> items(@RequestParam(required = false) String status) {
        return ApiResponse.ok(adminService.items(status));
    }

    @GetMapping("/items/{id}")
    public ApiResponse<Map<String, Object>> item(@PathVariable Long id) {
        return ApiResponse.ok(adminService.item(id));
    }

    @GetMapping("/items/{id}/payload")
    public ApiResponse<Object> payload(@PathVariable Long id) {
        return ApiResponse.ok(adminService.latestPayload(id));
    }

    /** body: { decision: APPROVED|REJECTED|OFFLINE, comment } */
    @PostMapping("/items/{id}/review")
    public ApiResponse<Map<String, Object>> review(@RequestAttribute(HubAuthFilter.ATTR_ADMIN) String admin,
                                                   @PathVariable Long id, @RequestBody Map<String, String> body) {
        return ApiResponse.ok(adminService.review(id, body.get("decision"), body.get("comment"), admin));
    }

    /** body: { visibility: PUBLIC|RESTRICTED } */
    @PutMapping("/items/{id}/visibility")
    public ApiResponse<Map<String, Object>> visibility(@PathVariable Long id, @RequestBody Map<String, String> body) {
        return ApiResponse.ok(adminService.updateVisibility(id, body.get("visibility")));
    }

    /** body: { tenantIds: [1,2,3] } 整体替换定向分享名单 */
    @PutMapping("/items/{id}/grants")
    public ApiResponse<Map<String, Object>> grants(@RequestAttribute(HubAuthFilter.ATTR_ADMIN) String admin,
                                                   @PathVariable Long id, @RequestBody Map<String, Object> body) {
        List<Long> ids = new ArrayList<>();
        Object raw = body.get("tenantIds");
        if (raw instanceof List) {
            for (Object o : (List<?>) raw) {
                ids.add(Long.valueOf(String.valueOf(o)));
            }
        }
        return ApiResponse.ok(adminService.replaceGrants(id, ids, admin));
    }

    @GetMapping("/tenants")
    public ApiResponse<List<Map<String, Object>>> tenants() {
        return ApiResponse.ok(adminService.tenants());
    }

    /** body: { code, name } → 返回含 apiKey(仅此一次) */
    @PostMapping("/tenants")
    public ApiResponse<Map<String, Object>> createTenant(@RequestBody Map<String, String> body) {
        return ApiResponse.ok(adminService.createTenant(body.get("code"), body.get("name")));
    }

    @PostMapping("/tenants/{id}/rotate-key")
    public ApiResponse<Map<String, Object>> rotateKey(@PathVariable Long id) {
        return ApiResponse.ok(adminService.rotateKey(id));
    }

    @PutMapping("/tenants/{id}/status")
    public ApiResponse<Map<String, Object>> tenantStatus(@PathVariable Long id, @RequestBody Map<String, String> body) {
        return ApiResponse.ok(adminService.updateTenantStatus(id, body.get("status")));
    }
}
