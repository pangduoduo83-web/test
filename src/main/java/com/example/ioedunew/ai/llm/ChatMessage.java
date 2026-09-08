package com.example.ioedunew.ai.llm;

import lombok.Data;

import java.util.ArrayList;
import java.util.List;

/** 对话消息(OpenAI 兼容语义):role 为 system / user / assistant / tool */
@Data
public class ChatMessage {

    private String role;
    private String content;
    /** assistant 消息发起的工具调用 */
    private List<ToolCall> toolCalls = new ArrayList<>();
    /** tool 消息对应的调用 id 与工具名 */
    private String toolCallId;
    private String name;

    public static ChatMessage system(String content) {
        return of("system", content);
    }

    public static ChatMessage user(String content) {
        return of("user", content);
    }

    public static ChatMessage assistant(String content) {
        return of("assistant", content);
    }

    public static ChatMessage tool(String toolCallId, String name, String content) {
        ChatMessage m = of("tool", content);
        m.toolCallId = toolCallId;
        m.name = name;
        return m;
    }

    private static ChatMessage of(String role, String content) {
        ChatMessage m = new ChatMessage();
        m.role = role;
        m.content = content;
        return m;
    }
}
