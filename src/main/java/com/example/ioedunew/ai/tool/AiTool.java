package com.example.ioedunew.ai.tool;

import com.example.ioedunew.ai.llm.ToolSpec;
import com.fasterxml.jackson.databind.JsonNode;

/**
 * 可被模型调用的工具。定义形状 {name, description, inputSchema} 与 MCP Tool / OpenAI function 一致,
 * 日后接 MCP Server 或 Spring AI ToolCallback 时可一对一映射,不需要改工具实现。
 * 实现类注册为 Spring bean 即自动进入 ToolRegistry。
 */
public interface AiTool {

    /** 唯一名称,形如 equipment.search;只允许字母数字点下划线 */
    String name();

    String description();

    /** 参数 JSON Schema(type=object) */
    JsonNode inputSchema();

    /** 只读工具可直接执行;写操作(destructive=true)默认需要用户确认后才执行 */
    default boolean readOnly() {
        return true;
    }

    /** 需要的最低角色:null 表示所有登录用户;"TEACHER" 表示教师或管理员;"ADMIN" 仅管理员 */
    default String requiredRole() {
        return null;
    }

    JsonNode execute(ToolContext ctx, JsonNode args);

    default ToolSpec spec() {
        return new ToolSpec(name(), description(), inputSchema());
    }
}
