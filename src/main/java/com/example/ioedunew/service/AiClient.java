package com.example.ioedunew.service;

import com.example.ioedunew.ai.llm.ChatMessage;
import com.example.ioedunew.ai.llm.ChatRequest;
import com.example.ioedunew.ai.llm.ChatResult;
import com.example.ioedunew.ai.llm.LlmGateway;
import com.example.ioedunew.ai.llm.StreamListener;
import com.example.ioedunew.tenant.TenantContext;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Semaphore;
import java.util.concurrent.TimeUnit;

/**
 * 大模型调用的稳定性外壳:配置来自 AiConfigService(每个租户的 system_settings,管理后台可改,即时生效),
 * 实际协议由 LlmGateway 实现。策略:全局 + 每租户并发上限、每租户连续失败短路熔断
 * (一个客户填错 Key 不会熔断其他客户);未配置或已停用时由调用方走规则降级。
 */
@Component
public class AiClient {

    private static final Logger log = LoggerFactory.getLogger(AiClient.class);

    /** 整个进程最多同时 16 个模型请求,避免拖垮后端线程池;每个租户最多 4 个 */
    private final Semaphore globalConcurrency = new Semaphore(16);
    private static final int PER_TENANT_CONCURRENCY = 4;

    /** 连续失败 3 次后熔断 60 秒,期间直接走降级 */
    private static final int CIRCUIT_THRESHOLD = 3;
    private static final long CIRCUIT_COOLDOWN_MS = 60_000L;

    private final ConcurrentHashMap<String, TenantState> states = new ConcurrentHashMap<>();

    private final AiConfigService configService;
    private final LlmGateway gateway;

    public AiClient(AiConfigService configService, LlmGateway gateway) {
        this.configService = configService;
        this.gateway = gateway;
    }

    /** 是否已配置且启用(未就绪时调用方应直接走规则降级) */
    public boolean isConfigured() {
        return configService.effective().isReady();
    }

    /**
     * 发起一次 JSON 模式的对话补全,返回模型输出的 content 文本。
     * 任何失败(未配置/熔断/超时/响应异常)都抛 AiUnavailableException。
     */
    public String chatJson(String systemPrompt, String userPrompt, int maxTokens) {
        ChatRequest req = new ChatRequest();
        req.getMessages().add(ChatMessage.system(systemPrompt));
        req.getMessages().add(ChatMessage.user(userPrompt));
        req.setMaxTokens(maxTokens);
        req.setJsonMode(true);
        String content = chat(req).getContent();
        if (content == null || content.trim().isEmpty()) {
            throw new AiUnavailableException("模型响应为空");
        }
        return content.trim();
    }

    /** 通用非流式补全(支持工具调用),受并发闸与熔断保护 */
    public ChatResult chat(ChatRequest request) {
        return guarded(cfg -> gateway.chat(cfg, request));
    }

    /** 流式补全,受并发闸与熔断保护;监听器在调用线程上回调 */
    public void stream(ChatRequest request, StreamListener listener) {
        guarded(cfg -> {
            gateway.stream(cfg, request, listener);
            return null;
        });
    }

    private <T> T guarded(Call<T> call) {
        AiConfigService.AiConfig cfg = configService.effective();
        if (!cfg.isReady()) {
            throw new AiUnavailableException("AI 服务未配置或已停用");
        }
        TenantState state = state();
        if (System.currentTimeMillis() < state.circuitOpenUntil) {
            throw new AiUnavailableException("AI 服务熔断中");
        }
        boolean tenantAcquired = false;
        boolean globalAcquired = false;
        try {
            tenantAcquired = state.concurrency.tryAcquire(2, TimeUnit.SECONDS);
            if (!tenantAcquired) {
                throw new AiUnavailableException("AI 服务繁忙");
            }
            globalAcquired = globalConcurrency.tryAcquire(2, TimeUnit.SECONDS);
            if (!globalAcquired) {
                throw new AiUnavailableException("AI 服务繁忙");
            }
            long start = System.currentTimeMillis();
            T result = call.run(cfg);
            state.consecutiveFailures = 0;
            log.info("AI 调用成功 tenant={} model={} 耗时={}ms", TenantContext.get(), cfg.model,
                    System.currentTimeMillis() - start);
            return result;
        } catch (AiUnavailableException e) {
            throw e;
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new AiUnavailableException("AI 调用被中断");
        } catch (Exception e) {
            recordFailure(state);
            log.warn("AI 调用失败 tenant={}: {}", TenantContext.get(), e.getMessage());
            throw new AiUnavailableException("AI 调用失败:" + e.getMessage());
        } finally {
            if (globalAcquired) {
                globalConcurrency.release();
            }
            if (tenantAcquired) {
                state.concurrency.release();
            }
        }
    }

    /** 连接测试:发一个极小请求,返回延迟与模型信息(供管理后台"测试连接") */
    public Map<String, Object> ping() {
        AiConfigService.AiConfig cfg = configService.effective();
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("model", cfg.model);
        result.put("baseUrl", cfg.baseUrl);
        if (!cfg.isReady()) {
            result.put("ok", false);
            result.put("error", cfg.enabled ? "尚未配置 API Key" : "AI 功能已停用");
            return result;
        }
        long start = System.currentTimeMillis();
        try {
            ChatRequest req = new ChatRequest();
            req.getMessages().add(ChatMessage.system("你是连接测试助手,只输出 JSON:{\"pong\":true}"));
            req.getMessages().add(ChatMessage.user("{\"ping\":true}"));
            req.setMaxTokens(30);
            req.setJsonMode(true);
            String content = gateway.chat(cfg, req).getContent();
            TenantState state = state();
            state.consecutiveFailures = 0;
            state.circuitOpenUntil = 0L;
            result.put("ok", true);
            result.put("latencyMs", System.currentTimeMillis() - start);
            result.put("reply", content.length() > 60 ? content.substring(0, 60) : content);
        } catch (Exception e) {
            result.put("ok", false);
            result.put("latencyMs", System.currentTimeMillis() - start);
            result.put("error", e.getMessage());
        }
        return result;
    }

    private TenantState state() {
        return states.computeIfAbsent(TenantContext.require(), k -> new TenantState());
    }

    private void recordFailure(TenantState state) {
        state.consecutiveFailures++;
        if (state.consecutiveFailures >= CIRCUIT_THRESHOLD) {
            state.circuitOpenUntil = System.currentTimeMillis() + CIRCUIT_COOLDOWN_MS;
            state.consecutiveFailures = 0;
            log.warn("AI 连续失败,租户 {} 熔断 {} 秒", TenantContext.get(), CIRCUIT_COOLDOWN_MS / 1000);
        }
    }

    private interface Call<T> {
        T run(AiConfigService.AiConfig cfg) throws Exception;
    }

    /** 每个租户独立的并发闸与熔断状态 */
    private static class TenantState {
        final Semaphore concurrency = new Semaphore(PER_TENANT_CONCURRENCY);
        volatile int consecutiveFailures = 0;
        volatile long circuitOpenUntil = 0L;
    }

    /** AI 暂不可用(未配置/熔断/超时等),调用方据此降级 */
    public static class AiUnavailableException extends RuntimeException {
        public AiUnavailableException(String message) {
            super(message);
        }
    }
}
