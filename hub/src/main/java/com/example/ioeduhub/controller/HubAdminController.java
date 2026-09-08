package com.example.ioeduhub.controller;

import com.example.ioeduhub.common.ApiResponse;
import com.example.ioeduhub.common.BusinessException;
import com.example.ioeduhub.config.HubAuthFilter;
import com.example.ioeduhub.service.HubAdminService;
import com.example.ioeduhub.service.SiteService;
import com.fasterxml.jackson.databind.JsonNode;
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

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/** 平台管理员接口(登录后 JWT 鉴权) */
@RestController
@RequestMapping("/api/hub-admin")
public class HubAdminController {

    private final HubAdminService adminService;
    private final SiteService siteService;

    public HubAdminController(HubAdminService adminService, SiteService siteService) {
        this.adminService = adminService;
        this.siteService = siteService;
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

    @GetMapping("/items/{id}/installs")
    public ApiResponse<Map<String, Object>> installs(@PathVariable Long id) {
        return ApiResponse.ok(adminService.installsByTenant(id));
    }

    /** body: { decision: APPROVED|REJECTED|OFFLINE, comment } */
    @PostMapping("/items/{id}/review")
    public ApiResponse<Map<String, Object>> review(@RequestAttribute(HubAuthFilter.ATTR_ADMIN) String admin,
                                                   @PathVariable Long id, @RequestBody Map<String, String> body) {
        return ApiResponse.ok(adminService.review(id, body.get("decision"), body.get("comment"), admin));
    }

    /** body: { ids: [1,2], decision: APPROVED|REJECTED|OFFLINE, comment } → { ok, failed:[{id,message}] } */
    @PostMapping("/items/batch-review")
    public ApiResponse<Map<String, Object>> batchReview(@RequestAttribute(HubAuthFilter.ATTR_ADMIN) String admin,
                                                        @RequestBody Map<String, Object> body) {
        List<Long> ids = longList(body.get("ids"));
        if (ids.isEmpty()) {
            throw new BusinessException("请先勾选要处理的条目");
        }
        return ApiResponse.ok(adminService.batchReview(ids, str(body.get("decision")), str(body.get("comment")), admin));
    }

    /** body: { featured: true|false } */
    @PutMapping("/items/{id}/featured")
    public ApiResponse<Map<String, Object>> featured(@PathVariable Long id, @RequestBody Map<String, Object> body) {
        return ApiResponse.ok(adminService.setFeatured(id, Boolean.TRUE.equals(body.get("featured"))));
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
        return ApiResponse.ok(adminService.replaceGrants(id, longList(body.get("tenantIds")), admin));
    }

    private static List<Long> longList(Object raw) {
        List<Long> ids = new ArrayList<>();
        if (raw instanceof List) {
            for (Object o : (List<?>) raw) {
                ids.add(Long.valueOf(String.valueOf(o)));
            }
        }
        return ids;
    }

    private static String str(Object o) {
        return o == null ? null : String.valueOf(o);
    }

    // ---------- 客户站点(联动多租户主系统) ----------

    @GetMapping("/sites/config")
    public ApiResponse<Map<String, Object>> sitesConfig() {
        return ApiResponse.ok(siteService.config());
    }

    @GetMapping("/sites")
    public ApiResponse<List<Map<String, Object>>> sites() {
        return ApiResponse.ok(siteService.list());
    }

    /** body: { code, name, customDomain?, adminEmail?, adminPassword?, seedDemo? } */
    @PostMapping("/sites")
    public ApiResponse<Map<String, Object>> provisionSite(@RequestBody JsonNode body) {
        return ApiResponse.ok(siteService.provision(body));
    }

    @PutMapping("/sites/{code}/status")
    public ApiResponse<Map<String, Object>> siteStatus(@PathVariable String code, @RequestBody Map<String, String> body) {
        return ApiResponse.ok(siteService.updateStatus(code, body.get("status")));
    }

    @GetMapping("/sites/usage")
    public ApiResponse<JsonNode> sitesUsage() {
        return ApiResponse.ok(siteService.usage());
    }

    /** body: { plan?, maxUsers?, storageLimitMb?, aiMonthlyTokens?, expiresAt? },传 null 表示清除该限制 */
    @PutMapping("/sites/{code}/quota")
    public ApiResponse<JsonNode> siteQuota(@PathVariable String code, @RequestBody JsonNode body) {
        return ApiResponse.ok(siteService.updateQuota(code, body));
    }

    @PostMapping("/sites/{code}/rotate-key")
    public ApiResponse<Map<String, Object>> siteRotateKey(@PathVariable String code) {
        return ApiResponse.ok(siteService.rotateKey(code));
    }

    @DeleteMapping("/sites/{code}")
    public ApiResponse<Void> deleteSite(@PathVariable String code, @RequestParam String confirm,
                                        @RequestParam(defaultValue = "false") boolean dropData) {
        if (!code.equalsIgnoreCase(confirm)) {
            throw new BusinessException("请输入站点编码确认注销");
        }
        siteService.delete(code, dropData);
        return ApiResponse.ok();
    }

    // ---------- 商店客户(底层) ----------

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
