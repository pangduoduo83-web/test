package com.example.ioedunew.ai.tool;

import lombok.AllArgsConstructor;
import lombok.Data;

/** 工具执行时的调用者上下文:工具只能以该用户的身份与权限读写数据 */
@Data
@AllArgsConstructor
public class ToolContext {
    private Long userId;
    private String role;
    private String userName;
    private String tenant;

    public boolean isStaff() {
        return "ADMIN".equals(role) || "TEACHER".equals(role);
    }
}
