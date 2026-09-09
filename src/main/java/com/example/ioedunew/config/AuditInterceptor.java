package com.example.ioedunew.config;

import com.example.ioedunew.entity.AuditLog;
import com.example.ioedunew.entity.User;
import com.example.ioedunew.repository.AuditLogRepository;
import com.example.ioedunew.repository.UserRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.HandlerInterceptor;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.util.HashSet;
import java.util.Set;

/**
 * 操作审计:记录管理端 / 教师端 / 平台接口的所有写请求(POST/PUT/DELETE)是谁在何时做的、结果状态码。
 * 只记元数据不记请求体,避免把密码、密钥写进日志;记录失败只打日志,不影响业务。
 */
@Component
public class AuditInterceptor implements HandlerInterceptor {

    private static final Logger log = LoggerFactory.getLogger(AuditInterceptor.class);
    private static final Set<String> WRITE_METHODS = new HashSet<>(java.util.Arrays.asList("POST", "PUT", "DELETE", "PATCH"));

    private final AuditLogRepository repository;
    private final UserRepository userRepository;

    public AuditInterceptor(AuditLogRepository repository, UserRepository userRepository) {
        this.repository = repository;
        this.userRepository = userRepository;
    }

    @Override
    public void afterCompletion(HttpServletRequest request, HttpServletResponse response, Object handler, Exception ex) {
        if (!WRITE_METHODS.contains(request.getMethod())) {
            return;
        }
        String uri = request.getRequestURI();
        // 登录、AI 对话等高频或敏感接口不记
        if (uri.startsWith("/api/auth/") || uri.startsWith("/api/ai/chat")) {
            return;
        }
        try {
            AuditLog a = new AuditLog();
            AuthUser auth = (AuthUser) request.getAttribute(AuthUser.REQUEST_ATTR);
            if (auth != null) {
                a.setActorId(auth.getId());
                a.setActorRole(auth.getRole());
                a.setActorName(userRepository.findById(auth.getId()).map(User::getName).orElse(null));
            } else if (uri.startsWith("/api/platform/")) {
                a.setActorRole("PLATFORM");
                a.setActorName("平台令牌");
            }
            a.setMethod(request.getMethod());
            a.setPath(cut(uri + (request.getQueryString() == null ? "" : "?" + request.getQueryString()), 200));
            a.setSummary(summarize(request.getMethod(), uri));
            a.setStatus(response.getStatus());
            a.setIp(clientIp(request));
            repository.save(a);
        } catch (Exception e) {
            log.warn("写入审计日志失败: {}", e.getMessage());
        }
    }

    /** 把路径翻译成人能读的一句话,如 "删除 用户 #12" */
    static String summarize(String method, String uri) {
        String[] parts = uri.replaceFirst("^/api/", "").split("/");
        String area = parts.length > 0 ? parts[0] : "";
        String resource = parts.length > 1 ? parts[1] : "";
        String id = parts.length > 2 && parts[2].matches("\\d+|[a-z0-9-]+") ? parts[2] : null;
        String action = parts.length > 3 ? parts[3] : (parts.length > 2 && id == null ? parts[2] : null);
        String verb = "DELETE".equals(method) ? "删除" : "POST".equals(method) ? (id == null ? "新建" : "操作") : "修改";
        String res = RESOURCE_NAMES.getOrDefault(resource, resource);
        StringBuilder sb = new StringBuilder(verb).append(' ').append(res);
        if (id != null) {
            sb.append(" #").append(id);
        }
        if (action != null) {
            sb.append(" · ").append(ACTION_NAMES.getOrDefault(action, action));
        }
        if ("teacher".equals(area)) {
            sb.append("(教师端)");
        } else if ("platform".equals(area)) {
            sb.append("(平台)");
        }
        return cut(sb.toString(), 300);
    }

    private static final java.util.Map<String, String> RESOURCE_NAMES = new java.util.HashMap<>();
    private static final java.util.Map<String, String> ACTION_NAMES = new java.util.HashMap<>();

    static {
        RESOURCE_NAMES.put("users", "用户");
        RESOURCE_NAMES.put("projects", "项目");
        RESOURCE_NAMES.put("equipment", "设备");
        RESOURCE_NAMES.put("borrows", "借阅");
        RESOURCE_NAMES.put("enrollments", "报名");
        RESOURCE_NAMES.put("submissions", "成果");
        RESOURCE_NAMES.put("notifications", "通知");
        RESOURCE_NAMES.put("discussions", "讨论");
        RESOURCE_NAMES.put("skill-dimensions", "技能维度");
        RESOURCE_NAMES.put("ai-settings", "AI 设置");
        RESOURCE_NAMES.put("site-settings", "站点设置");
        RESOURCE_NAMES.put("store-settings", "商店接入设置");
        RESOURCE_NAMES.put("classes", "班级");
        RESOURCE_NAMES.put("tenants", "站点");
        RESOURCE_NAMES.put("ai", "AI 中心");
        RESOURCE_NAMES.put("publish", "商店发布");
        RESOURCE_NAMES.put("items", "商店条目");
        ACTION_NAMES.put("grade", "评分");
        ACTION_NAMES.put("return", "退回修改");
        ACTION_NAMES.put("ai-review", "AI 预评审");
        ACTION_NAMES.put("decide", "审批");
        ACTION_NAMES.put("confirm-return", "归还验收");
        ACTION_NAMES.put("reset-password", "重置密码");
        ACTION_NAMES.put("members", "成员");
        ACTION_NAMES.put("assignments", "作业");
        ACTION_NAMES.put("announcements", "公告");
        ACTION_NAMES.put("announce", "公告");
        ACTION_NAMES.put("remind", "提醒学生");
        ACTION_NAMES.put("featured", "推荐置顶");
        ACTION_NAMES.put("visibility", "可见范围");
        ACTION_NAMES.put("review", "审核");
        ACTION_NAMES.put("resources", "资源");
        ACTION_NAMES.put("cover", "封面");
        ACTION_NAMES.put("status", "状态");
        ACTION_NAMES.put("store-key", "商店密钥");
        ACTION_NAMES.put("test", "测试连接");
        ACTION_NAMES.put("promote", "设为共享");
        ACTION_NAMES.put("tools", "工具策略");
        ACTION_NAMES.put("skills", "SKILL");
    }

    private static String clientIp(HttpServletRequest request) {
        String xff = request.getHeader("X-Forwarded-For");
        String ip = xff != null && !xff.isEmpty() ? xff.split(",")[0].trim() : request.getHeader("X-Real-IP");
        if (ip == null || ip.isEmpty()) {
            ip = request.getRemoteAddr();
        }
        return cut(ip, 45);
    }

    private static String cut(String v, int max) {
        return v == null ? null : (v.length() <= max ? v : v.substring(0, max));
    }
}
