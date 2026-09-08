package com.example.ioedunew.config;

import lombok.AllArgsConstructor;
import lombok.Data;

/**
 * 认证上下文用户:由拦截器解析 JWT 后写入请求属性。
 * tenant 为签发令牌时所在的租户编码,拦截器会校验它与当前请求解析出的租户一致。
 */
@Data
@AllArgsConstructor
public class AuthUser {

    public static final String REQUEST_ATTR = "AUTH_USER";

    private Long id;
    private String role;
    private String tenant;

    public boolean isAdmin() {
        return "ADMIN".equals(role);
    }

    public boolean isTeacher() {
        return "TEACHER".equals(role);
    }
}
