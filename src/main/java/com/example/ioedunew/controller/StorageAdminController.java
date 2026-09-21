package com.example.ioedunew.controller;

import com.example.ioedunew.common.*;
import com.example.ioedunew.service.UploadStorage;
import com.example.ioedunew.tenant.*;
import org.springframework.web.bind.annotation.*;
import java.io.IOException;
import java.util.Map;

/** Platform token only; tenant admins cannot move another tenant's files. */
@RestController
@RequestMapping("/api/platform/tenants/{code}/storage")
public class StorageAdminController {
    private final TenantRegistry registry;
    private final UploadStorage storage;
    public StorageAdminController(TenantRegistry registry, UploadStorage storage) { this.registry = registry; this.storage = storage; }

    @PostMapping("/migrate")
    public ApiResponse<Map<String, Object>> migrate(@PathVariable String code,
            @RequestParam(defaultValue = "true") boolean dryRun, @RequestParam(defaultValue = "10") int limit) {
        Tenant tenant = registry.findByCode(code).orElseThrow(() -> new BusinessException(404, "商户不存在"));
        return ApiResponse.ok(TenantContext.runAs(tenant.getCode(), () -> {
            try { return storage.migrate(limit, dryRun); }
            catch (IOException e) { throw new BusinessException(500, "读取旧文件失败，请检查目录权限"); }
        }));
    }
}
