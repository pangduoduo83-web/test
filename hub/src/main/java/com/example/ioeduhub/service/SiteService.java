package com.example.ioeduhub.service;

import com.example.ioeduhub.common.BusinessException;
import com.example.ioeduhub.config.HubProperties;
import com.example.ioeduhub.entity.HubTenant;
import com.example.ioeduhub.repository.TenantRepo;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Service;
import org.springframework.web.client.HttpStatusCodeException;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestTemplate;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * 「客户站点」编排:把多租户主系统的租户开通接口(/api/platform/**)与商店的客户登记合成一步——
 * 开通站点时自动在商店登记该客户、签发 API Key 并写入新站点的接入设置,客户拿到手即可使用商店。
 */
@Service
public class SiteService {

    private static final Logger log = LoggerFactory.getLogger(SiteService.class);

    private final HubProperties props;
    private final TenantRepo tenantRepo;
    private final HubAdminService adminService;
    private final ObjectMapper om;
    private final RestTemplate rest;

    public SiteService(HubProperties props, TenantRepo tenantRepo, HubAdminService adminService, ObjectMapper om) {
        this.props = props;
        this.tenantRepo = tenantRepo;
        this.adminService = adminService;
        this.om = om;
        SimpleClientHttpRequestFactory f = new SimpleClientHttpRequestFactory();
        f.setConnectTimeout(3000);
        f.setReadTimeout(120000);
        this.rest = new RestTemplate(f);
    }

    public Map<String, Object> config() {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("configured", props.platformConfigured());
        m.put("platformApiUrl", props.getPlatformApiUrl());
        m.put("siteUrlTemplate", props.getSiteUrlTemplate());
        return m;
    }

    /** 站点列表 = 主系统租户 ⨝ 商店客户(按编码) */
    public List<Map<String, Object>> list() {
        requireConfigured();
        JsonNode tenants = call(HttpMethod.GET, "/api/platform/tenants", null);
        Map<String, HubTenant> hubByCode = new HashMap<>();
        for (HubTenant t : tenantRepo.findAll()) {
            hubByCode.put(t.getCode(), t);
        }
        List<Map<String, Object>> out = new ArrayList<>();
        for (JsonNode t : tenants) {
            Map<String, Object> m = new LinkedHashMap<>();
            String code = t.path("code").asText();
            m.put("code", code);
            m.put("name", t.path("name").asText());
            m.put("dbName", t.path("dbName").asText());
            m.put("customDomain", t.path("customDomain").asText(null));
            m.put("status", t.path("status").asText());
            m.put("createdAt", t.path("createdAt").asText(null));
            m.put("siteUrl", siteUrl(code, t.path("customDomain").asText(null)));
            HubTenant h = hubByCode.get(code);
            m.put("storeRegistered", h != null);
            m.put("storeTenantId", h == null ? null : h.getId());
            m.put("storeStatus", h == null ? null : h.getStatus());
            out.add(m);
        }
        return out;
    }

    /**
     * 一键开通:商店登记(已存在则重签 Key)→ 主系统建站并写入 Key。
     * body: { code, name, customDomain?, adminEmail?, adminPassword?, seedDemo? }
     */
    public Map<String, Object> provision(JsonNode body) {
        requireConfigured();
        String code = body.path("code").asText("").trim().toLowerCase(Locale.ROOT);
        String name = body.path("name").asText("").trim();
        if (code.isEmpty() || name.isEmpty()) {
            throw new BusinessException("站点编码与名称不能为空");
        }
        HubTenant existing = tenantRepo.findByCode(code).orElse(null);
        Map<String, Object> keyResult;
        if (existing == null) {
            keyResult = adminService.createTenant(code, name);
        } else {
            // 同一编码注销后重新开通:沿用商店侧记录,恢复启用并重签 Key
            existing.setName(name);
            existing.setStatus("ACTIVE");
            tenantRepo.save(existing);
            keyResult = adminService.rotateKey(existing.getId());
        }
        String apiKey = String.valueOf(keyResult.get("apiKey"));

        ObjectNode req = om.createObjectNode();
        req.put("code", code);
        req.put("name", name);
        req.put("customDomain", body.path("customDomain").asText(""));
        req.put("adminEmail", body.path("adminEmail").asText(""));
        req.put("adminPassword", body.path("adminPassword").asText(""));
        req.put("seedDemo", body.path("seedDemo").asBoolean(false));
        req.put("hubApiKey", apiKey);
        JsonNode site = call(HttpMethod.POST, "/api/platform/tenants", req);

        Map<String, Object> m = new LinkedHashMap<>();
        m.put("code", code);
        m.put("name", name);
        m.put("status", site.path("status").asText());
        m.put("dbName", site.path("dbName").asText());
        m.put("adminEmail", site.path("adminEmail").asText());
        m.put("initialAdminPassword", site.path("initialAdminPassword").asText(null));
        m.put("siteUrl", siteUrl(code, body.path("customDomain").asText(null)));
        m.put("siteHost", site.path("siteHost").asText(null));
        m.put("storeKeyConfigured", site.path("storeKeyConfigured").asBoolean(false));
        m.put("storeApiKey", site.path("storeKeyConfigured").asBoolean(false) ? null : apiKey);
        log.info("已开通客户站点 {}(商店 Key 自动写入: {})", code, m.get("storeKeyConfigured"));
        return m;
    }

    public Map<String, Object> updateStatus(String code, String status) {
        requireConfigured();
        ObjectNode req = om.createObjectNode().put("status", status);
        JsonNode site = call(HttpMethod.PUT, "/api/platform/tenants/" + code + "/status", req);
        tenantRepo.findByCode(code).ifPresent(t -> {
            t.setStatus("SUSPENDED".equals(status) ? "SUSPENDED" : "ACTIVE");
            tenantRepo.save(t);
        });
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("code", code);
        m.put("status", site.path("status").asText());
        return m;
    }

    /** 注销站点:主系统删除(可选删库删文件),商店侧停用该客户并保留其发布记录 */
    public void delete(String code, boolean dropData) {
        requireConfigured();
        call(HttpMethod.DELETE, "/api/platform/tenants/" + code + "?confirm=" + code + "&dropData=" + dropData, null);
        tenantRepo.findByCode(code).ifPresent(t -> {
            t.setStatus("SUSPENDED");
            tenantRepo.save(t);
        });
    }

    /** 重签商店 Key 并推送到站点(站点原 Key 立即失效) */
    public Map<String, Object> rotateKey(String code) {
        requireConfigured();
        HubTenant t = tenantRepo.findByCode(code).orElse(null);
        Map<String, Object> keyResult = t == null ? adminService.createTenant(code, code) : adminService.rotateKey(t.getId());
        String apiKey = String.valueOf(keyResult.get("apiKey"));
        boolean pushed;
        try {
            call(HttpMethod.PUT, "/api/platform/tenants/" + code + "/store-key", om.createObjectNode().put("apiKey", apiKey));
            pushed = true;
        } catch (BusinessException e) {
            log.warn("向站点 {} 推送商店 Key 失败: {}", code, e.getMessage());
            pushed = false;
        }
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("code", code);
        m.put("pushed", pushed);
        m.put("apiKey", pushed ? null : apiKey);
        return m;
    }

    private String siteUrl(String code, String customDomain) {
        if (customDomain != null && !customDomain.isEmpty()) {
            return "https://" + customDomain;
        }
        String tpl = props.getSiteUrlTemplate();
        if (tpl.isEmpty()) {
            return null;
        }
        // 默认站点走根域 www,而不是 default.根域
        return tpl.replace("{code}", "default".equals(code) ? "www" : code);
    }

    private void requireConfigured() {
        if (!props.platformConfigured()) {
            throw new BusinessException(412, "商店服务未配置客户站点后端(HUB_PLATFORM_API_URL / HUB_PLATFORM_TOKEN),无法管理站点");
        }
    }

    private JsonNode call(HttpMethod method, String path, JsonNode body) {
        HttpHeaders headers = new HttpHeaders();
        headers.set("Authorization", "Bearer " + props.getPlatformToken());
        headers.setContentType(new MediaType(MediaType.APPLICATION_JSON, StandardCharsets.UTF_8));
        try {
            String json = body == null ? null : om.writeValueAsString(body);
            ResponseEntity<String> resp = rest.exchange(props.getPlatformApiUrl() + path, method,
                    new HttpEntity<>(json, headers), String.class);
            JsonNode root = om.readTree(resp.getBody() == null ? "{}" : resp.getBody());
            int code = root.path("code").asInt(-1);
            if (code != 0) {
                throw new BusinessException(code == -1 ? 502 : code, "客户站点后端:" + root.path("message").asText("未知错误"));
            }
            return root.path("data");
        } catch (HttpStatusCodeException e) {
            String msg;
            try {
                msg = om.readTree(e.getResponseBodyAsString()).path("message").asText(e.getStatusText());
            } catch (Exception ignored) {
                msg = "HTTP " + e.getRawStatusCode();
            }
            throw new BusinessException(e.getRawStatusCode(), "客户站点后端:" + msg);
        } catch (ResourceAccessException e) {
            throw new BusinessException(502, "无法连接客户站点后端: " + e.getMessage());
        } catch (BusinessException e) {
            throw e;
        } catch (Exception e) {
            throw new BusinessException(502, "客户站点后端响应异常: " + e.getMessage());
        }
    }
}
