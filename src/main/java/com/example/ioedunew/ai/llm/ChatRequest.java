package com.example.ioedunew.ai.llm;

import lombok.Data;

import java.util.ArrayList;
import java.util.List;

/** 一次对话补全请求(模型名与密钥由网关从租户配置补齐) */
@Data
public class ChatRequest {
    private List<ChatMessage> messages = new ArrayList<>();
    private List<ToolSpec> tools = new ArrayList<>();
    private Double temperature;
    private Integer maxTokens;
    /** 要求模型输出 JSON 对象(response_format=json_object) */
    private boolean jsonMode;
}
