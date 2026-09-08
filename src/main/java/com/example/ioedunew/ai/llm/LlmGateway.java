package com.example.ioedunew.ai.llm;

import com.example.ioedunew.service.AiConfigService;

/**
 * 大模型网关抽象:当前实现为 OpenAI 兼容协议(DeepSeek / 通义千问 / OpenAI 等);
 * 日后升级 Spring Boot 3 可换成 Spring AI ChatClient 实现,调用方不变。
 */
public interface LlmGateway {

    /** 非流式补全(支持工具调用与 JSON 模式) */
    ChatResult chat(AiConfigService.AiConfig cfg, ChatRequest request) throws Exception;

    /** 流式补全:文本增量经 listener 推送;模型发起工具调用时同样在 onComplete 的 result 中给出 */
    void stream(AiConfigService.AiConfig cfg, ChatRequest request, StreamListener listener) throws Exception;
}
