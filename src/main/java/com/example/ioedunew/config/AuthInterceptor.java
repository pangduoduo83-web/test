package com.example.ioedunew.config;

import org.springframework.stereotype.Component;
import org.springframework.web.servlet.HandlerInterceptor;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.nio.charset.StandardCharsets;

/**
 * 认证拦截器。
 * 职责:校验 /api 下请求的 Bearer 令牌,并把 AuthUser 写入请求属性;
 * /api/auth、/api/public 放行;/api/admin 要求 ADMIN 角色;
 * /api/teacher 要求 TEACHER 或 ADMIN 角色。
 * 失败语义:未认证返回 401,权限不足返回 403,均为统一 JSON 结构。
 */
@Component
public class AuthInterceptor implements HandlerInterceptor {

    private final JwtUtil jwtUtil;

    public AuthInterceptor(JwtUtil jwtUtil) {
        this.jwtUtil = jwtUtil;
    }

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) throws Exception {
        if ("OPTIONS".equalsIgnoreCase(request.getMethod())) {
            return true;
        }
        String uri = request.getRequestURI();
        if (uri.startsWith("/api/auth/") || uri.startsWith("/api/public/")) {
            // 白名单路径:携带了令牌就尽力解析(供 /api/auth/me 使用),解析失败不拦截
            String h = request.getHeader("Authorization");
            if (h != null && h.startsWith("Bearer ")) {
                try {
                    request.setAttribute(AuthUser.REQUEST_ATTR, jwtUtil.parseToken(h.substring(7)));
                } catch (Exception ignored) {
                }
            }
            return true;
        }

        String header = request.getHeader("Authorization");
        if (header == null || !header.startsWith("Bearer ")) {
            return reject(response, 401, "未登录或令牌缺失");
        }
        AuthUser user;
        try {
            user = jwtUtil.parseToken(header.substring(7));
        } catch (Exception e) {
            return reject(response, 401, "令牌无效或已过期");
        }
        if (uri.startsWith("/api/admin/") && !user.isAdmin()) {
            return reject(response, 403, "需要管理员权限");
        }
        if (uri.startsWith("/api/teacher/") && !user.isTeacher() && !user.isAdmin()) {
            return reject(response, 403, "需要教师权限");
        }
        request.setAttribute(AuthUser.REQUEST_ATTR, user);
        return true;
    }

    private boolean reject(HttpServletResponse response, int status, String message) throws Exception {
        response.setStatus(status);
        response.setContentType("application/json;charset=UTF-8");
        String body = "{\"code\":" + status + ",\"message\":\"" + message + "\",\"data\":null}";
        response.getOutputStream().write(body.getBytes(StandardCharsets.UTF_8));
        return false;
    }
}
