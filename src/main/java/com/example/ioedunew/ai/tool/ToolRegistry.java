package com.example.ioedunew.ai.tool;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.regex.Pattern;

/** 工具注册中心:收集全部 AiTool bean,按名称查找 */
@Component
public class ToolRegistry {

    private static final Logger log = LoggerFactory.getLogger(ToolRegistry.class);
    private static final Pattern NAME = Pattern.compile("^[A-Za-z][A-Za-z0-9_.]{1,63}$");

    private final Map<String, AiTool> tools = new LinkedHashMap<>();

    public ToolRegistry(List<AiTool> beans) {
        for (AiTool t : beans) {
            if (!NAME.matcher(t.name()).matches()) {
                throw new IllegalStateException("非法工具名: " + t.name());
            }
            if (tools.put(t.name(), t) != null) {
                throw new IllegalStateException("工具名重复: " + t.name());
            }
        }
        log.info("AI 工具注册中心已加载 {} 个工具: {}", tools.size(), tools.keySet());
    }

    public Optional<AiTool> find(String name) {
        return Optional.ofNullable(tools.get(name));
    }

    public List<AiTool> all() {
        return Collections.unmodifiableList(new ArrayList<>(tools.values()));
    }

    /** 角色是否满足工具的最低要求 */
    public static boolean roleAllows(AiTool tool, String role) {
        String required = tool.requiredRole();
        if (required == null) {
            return true;
        }
        if ("ADMIN".equals(required)) {
            return "ADMIN".equals(role);
        }
        if ("TEACHER".equals(required)) {
            return "ADMIN".equals(role) || "TEACHER".equals(role);
        }
        return true;
    }
}
