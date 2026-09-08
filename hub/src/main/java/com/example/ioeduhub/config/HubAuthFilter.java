package com.example.ioeduhub.config;

import com.example.ioeduhub.entity.HubTenant;
import com.example.ioeduhub.repository.TenantRepo;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import javax.servlet.FilterChain;
import javax.servlet.ServletException;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;

/**
 * 两套凭据:
 * - /api/store/**:客户实例的租户 API Key(Authorization: Bearer hk_...),按 SHA-256 哈希查租户;
 * - /api/hub-admin/**(登录接口除外):平台管理员 JWT。
 * 附件 /hub-assets/** 与健康检查公开。
 */
@Component
@Order(Ordered.HIGHEST_PRECEDENCE)
public class HubAuthFilter extends OncePerRequestFilter {

    public static final String ATTR_TENANT = "HUB_TENANT";
    public static final String ATTR_ADMIN = "HUB_ADMIN";
    /** 客户实例转发的操作人(姓名/角色),仅用于审计与展示 */
    public static final String HEADER_ACTING_USER = "X-Acting-User";

    private final TenantRepo tenantRepo;
    private final AdminJwt adminJwt;

    public HubAuthFilter(TenantRepo tenantRepo, AdminJwt adminJwt) {
        this.tenantRepo = tenantRepo;
        this.adminJwt = adminJwt;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        String uri = request.getRequestURI();
        if ("OPTIONS".equalsIgnoreCase(request.getMethod())) {
            chain.doFilter(request, response);
            return;
        }
        if (uri.startsWith("/api/store/")) {
            String key = bearer(request);
            if (key == null || !key.startsWith("hk_")) {
                reject(response, 401, "缺少商店 API Key");
                return;
            }
            HubTenant tenant = tenantRepo.findByApiKeyHash(sha256(key)).orElse(null);
            if (tenant == null) {
                reject(response, 401, "商店 API Key 无效");
                return;
            }
            if (!"ACTIVE".equals(tenant.getStatus())) {
                reject(response, 403, "该客户已被停用商店访问");
                return;
            }
            request.setAttribute(ATTR_TENANT, tenant);
        } else if (uri.startsWith("/api/hub-admin/") && !uri.equals("/api/hub-admin/login")) {
            String token = bearer(request);
            if (token == null) {
                reject(response, 401, "未登录");
                return;
            }
            try {
                request.setAttribute(ATTR_ADMIN, adminJwt.parse(token));
            } catch (Exception e) {
                reject(response, 401, "登录已过期,请重新登录");
                return;
            }
        }
        chain.doFilter(request, response);
    }

    private String bearer(HttpServletRequest request) {
        String header = request.getHeader("Authorization");
        if (header == null || !header.startsWith("Bearer ")) {
            return null;
        }
        String v = header.substring(7).trim();
        return v.isEmpty() ? null : v;
    }

    public static String sha256(String value) {
        try {
            MessageDigest md = MessageDigest.getInstance("SHA-256");
            byte[] digest = md.digest(value.getBytes(StandardCharsets.UTF_8));
            StringBuilder sb = new StringBuilder();
            for (byte b : digest) {
                sb.append(String.format("%02x", b));
            }
            return sb.toString();
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
    }

    private void reject(HttpServletResponse response, int status, String message) throws IOException {
        response.setStatus(status);
        response.setContentType("application/json;charset=UTF-8");
        response.getOutputStream().write(("{\"code\":" + status + ",\"message\":\"" + message + "\",\"data\":null}")
                .getBytes(StandardCharsets.UTF_8));
    }
}
