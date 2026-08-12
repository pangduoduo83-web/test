package com.example.ioedunew.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestTemplate;

import java.nio.charset.StandardCharsets;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.Semaphore;
import java.util.concurrent.TimeUnit;

/**
 * OpenAI 兼容协议的大模型客户端(DeepSeek / 通义千问等)。
 * 配置来自 AiConfigService(管理后台可改,即时生效,无需重启)。
 * 稳定性策略:全局并发上限、连接/读取超时、连续失败短路熔断;
 * 未配置或已停用时由调用方走规则降级。
 */
@Component
public class AiClient {

    private static final Logger log = LoggerFactory.getLogger(AiClient.class);

    /** 全局最多同时 4 个模型请求,避免拖垮后端线程池 */
    private final Semaphore concurrency = new Semaphore(4);

    /** 连续失败 3 次后熔断 60 秒,期间直接走降级 */
    private static final int CIRCUIT_THRESHOLD = 3;
    private static final long CIRCUIT_COOLDOWN_MS = 60_000L;
    private volatile int consecutiveFailures = 0;
    private volatile long circuitOpenUntil = 0L;

    private final AiConfigService configService;
    private final ObjectMapper objectMapper;

    public AiClient(AiConfigService configService, ObjectMapper objectMapper) {
        this.configService = configService;
        this.objectMapper = objectMapper;
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
        AiConfigService.AiConfig cfg = configService.effective();
        if (!cfg.isReady()) {
            throw new AiUnavailableException("AI 服务未配置或已停用");
        }
        if (System.currentTimeMillis() < circuitOpenUntil) {
            throw new AiUnavailableException("AI 服务熔断中");
        }
        boolean acquired = false;
        try {
            acquired = concurrency.tryAcquire(2, TimeUnit.SECONDS);
            if (!acquired) {
                throw new AiUnavailableException("AI 服务繁忙");
            }
            long start = System.currentTimeMillis();
            String content = doCall(cfg, systemPrompt, userPrompt, Math.min(maxTokens, cfg.maxTokens));
            consecutiveFailures = 0;
            log.info("AI 调用成功 model={} 耗时={}ms 输出={}字", cfg.model, System.currentTimeMillis() - start, content.length());
            return content;
        } catch (AiUnavailableException e) {
            throw e;
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new AiUnavailableException("AI 调用被中断");
        } catch (Exception e) {
            recordFailure();
            log.warn("AI 调用失败: {}", e.getMessage());
            throw new AiUnavailableException("AI 调用失败:" + e.getMessage());
        } finally {
            if (acquired) {
                concurrency.release();
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
            String content = doCall(cfg,
                    "你是连接测试助手,只输出 JSON:{\"pong\":true}",
                    "{\"ping\":true}", 30);
            consecutiveFailures = 0;
            circuitOpenUntil = 0L;
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

    private String doCall(AiConfigService.AiConfig cfg, String systemPrompt, String userPrompt,
                          int maxTokens) throws Exception {
        ObjectNode body = objectMapper.createObjectNode();
        body.put("model", cfg.model);
        body.put("temperature", cfg.temperature);
        body.put("max_tokens", maxTokens);
        body.put("stream", false);
        body.putObject("response_format").put("type", "json_object");
        ArrayNode messages = body.putArray("messages");
        messages.addObject().put("role", "system").put("content", systemPrompt);
        messages.addObject().put("role", "user").put("content", userPrompt);

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(new MediaType(MediaType.APPLICATION_JSON, StandardCharsets.UTF_8));
        headers.set("Authorization", "Bearer " + cfg.apiKey.trim());

        SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(cfg.connectTimeoutMs);
        factory.setReadTimeout(cfg.readTimeoutMs);
        RestTemplate restTemplate = new RestTemplate(factory);

        String url = cfg.baseUrl.replaceAll("/+$", "") + "/chat/completions";
        String response = restTemplate.postForObject(url,
                new HttpEntity<>(objectMapper.writeValueAsString(body), headers), String.class);

        JsonNode root = objectMapper.readTree(response);
        JsonNode content = root.path("choices").path(0).path("message").path("content");
        if (content.isMissingNode() || content.asText().trim().isEmpty()) {
            throw new IllegalStateException("模型响应为空");
        }
        return content.asText().trim();
    }

    private void recordFailure() {
        consecutiveFailures++;
        if (consecutiveFailures >= CIRCUIT_THRESHOLD) {
            circuitOpenUntil = System.currentTimeMillis() + CIRCUIT_COOLDOWN_MS;
            consecutiveFailures = 0;
            log.warn("AI 连续失败,熔断 {} 秒", CIRCUIT_COOLDOWN_MS / 1000);
        }
    }

    /** AI 暂不可用(未配置/熔断/超时等),调用方据此降级 */
    public static class AiUnavailableException extends RuntimeException {
        public AiUnavailableException(String message) {
            super(message);
        }
    }
}
