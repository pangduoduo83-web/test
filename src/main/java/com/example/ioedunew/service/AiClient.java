package com.example.ioedunew.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestTemplate;

import javax.annotation.PostConstruct;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.Semaphore;
import java.util.concurrent.TimeUnit;

/**
 * OpenAI 兼容协议的大模型客户端(DeepSeek / 通义千问等)。
 * 稳定性策略:全局并发上限、连接/读取超时、连续失败短路熔断;
 * api-key 未配置视为"未启用",由调用方走规则降级。
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

    @Value("${ioedu.ai.base-url}")
    private String baseUrl;

    @Value("${ioedu.ai.api-key}")
    private String apiKey;

    @Value("${ioedu.ai.model}")
    private String model;

    @Value("${ioedu.ai.connect-timeout-ms}")
    private int connectTimeoutMs;

    @Value("${ioedu.ai.read-timeout-ms}")
    private int readTimeoutMs;

    private final ObjectMapper objectMapper;
    private RestTemplate restTemplate;

    public AiClient(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    @PostConstruct
    void init() {
        SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(connectTimeoutMs);
        factory.setReadTimeout(readTimeoutMs);
        this.restTemplate = new RestTemplate(factory);
    }

    /** api-key 是否已配置(未配置时调用方应直接走规则降级) */
    public boolean isConfigured() {
        return apiKey != null && !apiKey.trim().isEmpty();
    }

    /**
     * 发起一次 JSON 模式的对话补全,返回模型输出的 content 文本。
     * 任何失败(未配置/熔断/超时/响应异常)都抛 AiUnavailableException。
     */
    public String chatJson(String systemPrompt, String userPrompt, int maxTokens) {
        if (!isConfigured()) {
            throw new AiUnavailableException("AI 服务未配置");
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
            String content = doCall(systemPrompt, userPrompt, maxTokens);
            consecutiveFailures = 0;
            log.info("AI 调用成功 model={} 耗时={}ms 输出={}字", model, System.currentTimeMillis() - start, content.length());
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

    private String doCall(String systemPrompt, String userPrompt, int maxTokens) throws Exception {
        ObjectNode body = objectMapper.createObjectNode();
        body.put("model", model);
        body.put("temperature", 0.4);
        body.put("max_tokens", maxTokens);
        body.put("stream", false);
        body.putObject("response_format").put("type", "json_object");
        ArrayNode messages = body.putArray("messages");
        messages.addObject().put("role", "system").put("content", systemPrompt);
        messages.addObject().put("role", "user").put("content", userPrompt);

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(new MediaType(MediaType.APPLICATION_JSON, StandardCharsets.UTF_8));
        headers.set("Authorization", "Bearer " + apiKey.trim());

        String url = baseUrl.replaceAll("/+$", "") + "/chat/completions";
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
