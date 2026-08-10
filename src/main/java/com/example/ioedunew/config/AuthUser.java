package com.example.ioedunew.config;

import lombok.AllArgsConstructor;
import lombok.Data;

/**
 * 认证上下文用户:由拦截器解析 JWT 后写入请求属性。
 */
@Data
@AllArgsConstructor
public class AuthUser {

    public static final String REQUEST_ATTR = "AUTH_USER";

    private Long id;
    private String role;

    public boolean isAdmin() {
        return "ADMIN".equals(role);
    }

    public boolean isTeacher() {
        return "TEACHER".equals(role);
    }
}
