package com.example.ioedunew.controller;

import com.example.ioedunew.common.ApiResponse;
import com.example.ioedunew.common.BusinessException;
import com.example.ioedunew.tenant.Tenant;
import com.example.ioedunew.tenant.TenantProvisioningService;
import com.example.ioedunew.tenant.TenantRegistry;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * 平台管理接口:租户列表 / 开通 / 启停 / 刷新缓存。
 * 由 PlatformTokenInterceptor 用静态平台令牌鉴权,不经过租户过滤器与用户 JWT。
 * 开通示例:
 * curl -X POST https://平台域名/api/platform/tenants -H "Authorization: Bearer $IOEDU_PLATFORM_TOKEN" \
 *      -H "Content-Type: application/json" \
 *      -d '{"code":"c001","name":"某某学院","adminEmail":"admin@c001.edu","seedDemo":false}'
 */
@RestController
@RequestMapping("/api/platform")
public class PlatformController {

    private final TenantRegistry registry;
    private final TenantProvisioningService provisioning;
    private final com.example.ioedunew.tenant.TenantQuotaService quota;

    @Value("${ioedu.upload-dir}")
    private String uploadDir;

    public PlatformController(TenantRegistry registry, TenantProvisioningService provisioning,
                              com.example.ioedunew.tenant.TenantQuotaService quota) {
        this.registry = registry;
        this.provisioning = provisioning;
        this.quota = quota;
    }

    @GetMapping("/tenants")
    public ApiResponse<List<Map<String, Object>>> list() {
        List<Map<String, Object>> list = new ArrayList<>();
        for (Tenant t : registry.all()) {
            list.add(provisioning.view(t));
        }
        return ApiResponse.ok(list);
    }

    /** body: { code, name, customDomain?, adminEmail?, adminPassword?, seedDemo?, hubApiKey? } */
    @PostMapping("/tenants")
    public ApiResponse<Map<String, Object>> create(@RequestBody Map<String, Object> body) {
        String code = str(body.get("code"));
        String name = str(body.get("name"));
        String customDomain = str(body.get("customDomain"));
        String adminEmail = str(body.get("adminEmail"));
        String adminPassword = str(body.get("adminPassword"));
        boolean seedDemo = Boolean.TRUE.equals(body.get("seedDemo"));
        String hubApiKey = str(body.get("hubApiKey"));
        return ApiResponse.ok(provisioning.provision(code, name, customDomain, adminEmail, adminPassword, seedDemo, hubApiKey));
    }

    /** 为已有租户写入/更换项目商店 API Key,body: { apiKey } */
    @PutMapping("/tenants/{code}/store-key")
    public ApiResponse<Map<String, Object>> storeKey(@PathVariable String code, @RequestBody Map<String, Object> body) {
        String apiKey = str(body.get("apiKey"));
        if (apiKey == null || apiKey.trim().isEmpty()) {
            throw new BusinessException("apiKey 不能为空");
        }
        boolean ok = provisioning.setStoreApiKey(code, apiKey.trim());
        if (!ok) {
            throw new BusinessException(500, "写入商店 API Key 失败(请检查主密钥 IOEDU_MASTER_KEY 是否配置)");
        }
        Map<String, Object> m = new java.util.LinkedHashMap<>();
        m.put("code", code);
        m.put("storeKeyConfigured", true);
        return ApiResponse.ok(m);
    }

    /** body: { status: ACTIVE | SUSPENDED } */
    @PutMapping("/tenants/{code}/status")
    public ApiResponse<Map<String, Object>> updateStatus(@PathVariable String code, @RequestBody Map<String, Object> body) {
        return ApiResponse.ok(provisioning.updateStatus(code, str(body.get("status"))));
    }

    /** 配额,body 里出现的字段才改:{ plan?, maxUsers?, storageLimitMb?, aiMonthlyTokens?, expiresAt?(yyyy-MM-dd) },传 null 清除 */
    @PutMapping("/tenants/{code}/quota")
    public ApiResponse<Map<String, Object>> updateQuota(@PathVariable String code, @RequestBody Map<String, Object> body) {
        return ApiResponse.ok(provisioning.updateQuota(code, body));
    }

    /** 全部站点的用量快照(用户数、7 天活跃、上传占用、本月 AI Token) */
    @GetMapping("/usage")
    public ApiResponse<List<Map<String, Object>>> usage() {
        List<Map<String, Object>> list = new ArrayList<>();
        for (Tenant t : registry.all()) {
            list.add(quota.usage(t));
        }
        return ApiResponse.ok(list);
    }

    @GetMapping("/tenants/{code}/usage")
    public ApiResponse<Map<String, Object>> tenantUsage(@PathVariable String code) {
        Tenant t = registry.findByCode(code).orElseThrow(() -> new BusinessException(404, "租户不存在: " + code));
        return ApiResponse.ok(quota.usage(t));
    }

    /**
     * 注销租户。必须以 confirm=租户编码 二次确认;dropData=true 时删除租户库与上传文件(不可恢复),
     * 默认只从注册表移除、保留数据。
     */
    @DeleteMapping("/tenants/{code}")
    public ApiResponse<Void> delete(@PathVariable String code,
                                    @RequestParam String confirm,
                                    @RequestParam(defaultValue = "false") boolean dropData) {
        if (!code.equalsIgnoreCase(confirm)) {
            throw new BusinessException("请以 confirm=" + code + " 确认注销");
        }
        provisioning.deprovision(code, dropData, Paths.get(uploadDir).toAbsolutePath().normalize());
        return ApiResponse.ok();
    }

    /** 多副本部署时,其他副本开通的租户可通过此接口立即刷新本副本缓存(否则最多延迟到下一次未命中回源) */
    @PostMapping("/tenants/refresh")
    public ApiResponse<Integer> refresh() {
        registry.refresh();
        return ApiResponse.ok(registry.all().size());
    }

    private String str(Object v) {
        return v == null ? null : String.valueOf(v);
    }
}
