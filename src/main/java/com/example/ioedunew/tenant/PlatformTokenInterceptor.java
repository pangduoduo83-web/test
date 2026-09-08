package com.example.ioedunew.tenant;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.HandlerInterceptor;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;

/**
 * 平台管理接口(/api/platform/**)鉴权:与租户用户体系无关,使用环境变量 IOEDU_PLATFORM_TOKEN 静态令牌。
 * 未配置令牌时平台接口整体关闭。
 */
@Component
public class PlatformTokenInterceptor implements HandlerInterceptor {

    @Value("${ioedu.platform.token:}")
    private String token;

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) throws Exception {
        if ("OPTIONS".equalsIgnoreCase(request.getMethod())) {
            return true;
        }
        if (token == null || token.trim().length() < 16) {
            return reject(response, 403, "平台管理接口未启用:请配置不少于 16 位的 IOEDU_PLATFORM_TOKEN");
        }
        String header = request.getHeader("Authorization");
        if (header == null || !header.startsWith("Bearer ")) {
            return reject(response, 401, "缺少平台令牌");
        }
        byte[] given = header.substring(7).trim().getBytes(StandardCharsets.UTF_8);
        byte[] expected = token.trim().getBytes(StandardCharsets.UTF_8);
        if (!MessageDigest.isEqual(given, expected)) {
            return reject(response, 401, "平台令牌无效");
        }
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
