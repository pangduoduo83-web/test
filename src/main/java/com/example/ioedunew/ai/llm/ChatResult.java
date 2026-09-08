package com.example.ioedunew.ai.llm;

import lombok.Data;

import java.util.ArrayList;
import java.util.List;

/** 模型一次回复:文本内容、工具调用请求、结束原因与 token 用量 */
@Data
public class ChatResult {
    private String content = "";
    private List<ToolCall> toolCalls = new ArrayList<>();
    private String finishReason;
    private int promptTokens;
    private int completionTokens;

    public boolean hasToolCalls() {
        return toolCalls != null && !toolCalls.isEmpty();
    }
}
