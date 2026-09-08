package com.example.ioedunew.tenant;

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
import java.util.Optional;

/**
 * 请求入口的租户解析:排在所有拦截器之前(AuthInterceptor 回查用户表时已经在正确的库上)。
 * 解析顺序:X-Tenant-Id 请求头(仅联调开启)→ Host(自有域名 / 子域名)→ 默认租户(strict-host=false 时)。
 * 平台管理接口与健康检查不属于任何租户,直接放行。
 */
@Component
@Order(Ordered.HIGHEST_PRECEDENCE)
public class TenantFilter extends OncePerRequestFilter {

    public static final String HEADER = "X-Tenant-Id";

    private final TenantRegistry registry;
    private final TenantProperties props;

    public TenantFilter(TenantRegistry registry, TenantProperties props) {
        this.registry = registry;
        this.props = props;
    }

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        String uri = request.getRequestURI();
        return uri.startsWith("/actuator") || uri.startsWith("/api/platform/");
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        Optional<Tenant> resolved = Optional.empty();
        String header = props.isAllowHeaderOverride() ? request.getHeader(HEADER) : null;
        if (header != null && !header.trim().isEmpty()) {
            resolved = registry.findByCode(header);
            if (!resolved.isPresent()) {
                reject(response, 404, "站点不存在: " + header.trim());
                return;
            }
        }
        if (!resolved.isPresent()) {
            resolved = registry.resolveByHost(request.getHeader("Host"));
        }
        if (!resolved.isPresent()) {
            if (props.isStrictHost()) {
                reject(response, 404, "站点不存在");
                return;
            }
            resolved = registry.defaultTenant();
            if (!resolved.isPresent()) {
                reject(response, 503, "平台尚未初始化默认站点");
                return;
            }
        }
        Tenant tenant = resolved.get();
        if (!tenant.isActive()) {
            reject(response, 403, "该站点已停用,请联系平台管理员");
            return;
        }
        if (tenant.isExpired()) {
            reject(response, 403, "该站点服务已于 " + tenant.getExpiresAt() + " 到期,请联系平台续期");
            return;
        }
        TenantContext.set(tenant.getCode());
        try {
            chain.doFilter(request, response);
        } finally {
            TenantContext.clear();
        }
    }

    private void reject(HttpServletResponse response, int status, String message) throws IOException {
        response.setStatus(status);
        response.setContentType("application/json;charset=UTF-8");
        String body = "{\"code\":" + status + ",\"message\":\"" + message.replace("\"", "'") + "\",\"data\":null}";
        response.getOutputStream().write(body.getBytes(StandardCharsets.UTF_8));
    }
}
