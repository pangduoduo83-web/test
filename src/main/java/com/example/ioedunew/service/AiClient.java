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
import org.springframework.util.DigestUtils;

import java.nio.charset.StandardCharsets;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Semaphore;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** 所有站点模型调用的稳定性边界：统一并发、配置检查、按能力隔离的熔断和安全错误信息。 */
@Component
public class AiClient {

    private static final Logger log = LoggerFactory.getLogger(AiClient.class);
    private static final Pattern HTTP_STATUS = Pattern.compile("\\bHTTP\\s+(\\d{3})\\b");
    private static final int PER_PROCESS_CONCURRENCY = 16;
    private static final int PER_TENANT_CONCURRENCY = 4;
    private static final int CIRCUIT_THRESHOLD = 3;
    private static final long CIRCUIT_COOLDOWN_MS = 60_000L;

    /** 并发按租户共享；熔断按租户 + 能力 + 目标配置隔离。 */
    private final Semaphore globalConcurrency = new Semaphore(PER_PROCESS_CONCURRENCY);
    private final ConcurrentHashMap<String, TenantState> tenants = new ConcurrentHashMap<String, TenantState>();
    private final ConcurrentHashMap<String, CircuitState> circuits = new ConcurrentHashMap<String, CircuitState>();

    private final AiConfigService configService;
    private final LlmGateway gateway;

    public AiClient(AiConfigService configService, LlmGateway gateway) {
        this.configService = configService;
        this.gateway = gateway;
    }

    public boolean isConfigured() {
        return ready(configService.effective());
    }

    public String chatJson(String systemPrompt, String userPrompt, int maxTokens) {
        ChatRequest req = new ChatRequest();
        req.getMessages().add(ChatMessage.system(systemPrompt));
        req.getMessages().add(ChatMessage.user(userPrompt));
        req.setMaxTokens(maxTokens);
        req.setJsonMode(true);
        String content = chat(req).getContent();
        if (content == null || content.trim().isEmpty()) throw new AiUnavailableException("模型响应为空");
        return content.trim();
    }

    /** 主模型调用。 */
    public ChatResult chat(ChatRequest request) {
        return chatWithConfig("main", configService.effective(), request);
    }

    /** 使用已保存的视觉、语音等媒体配置调用模型。 */
    public ChatResult chat(String service, ChatRequest request) {
        return chatWithConfig(service, mediaConfig(service), request);
    }

    /** 管理后台临时配置测试使用 probe:<service>，不污染生产熔断状态。 */
    public ChatResult chatWithConfig(final String scope, AiConfigService.AiConfig cfg, final ChatRequest request) {
        return guarded(scope, cfg, new CheckedCall<ChatResult>() {
            @Override
            public ChatResult run(AiConfigService.AiConfig current) throws Exception {
                return gateway.chat(current, request);
            }
        }, false);
    }

    /** 非 Chat Completions 的模型适配器（例如语音识别）也复用同一套并发和熔断边界。 */
    public <T> T execute(String scope, AiConfigService.AiConfig cfg, CheckedCall<T> call) throws Exception {
        return guarded(scope, cfg, call, true);
    }

    public void stream(final ChatRequest request, final StreamListener listener) {
        guarded("main", configService.effective(), new CheckedCall<Void>() {
            @Override
            public Void run(AiConfigService.AiConfig cfg) throws Exception {
                gateway.stream(cfg, request, listener);
                return null;
            }
        }, false);
    }

    private <T> T guarded(String scope, AiConfigService.AiConfig cfg, CheckedCall<T> call, boolean preserveLocalErrors) {
        if (!ready(cfg)) throw new AiUnavailableException("AI 服务未配置或已停用");
        String tenant = TenantContext.get() == null ? "default" : TenantContext.get();
        TenantState tenantState = tenants.computeIfAbsent(tenant, k -> new TenantState());
        CircuitState circuit = circuits.computeIfAbsent(circuitKey(tenant, scope, cfg), k -> new CircuitState());
        if (!circuit.tryEnter()) throw new AiUnavailableException("AI 服务熔断中");
        boolean tenantAcquired = false;
        boolean globalAcquired = false;
        try {
            tenantAcquired = tenantState.concurrency.tryAcquire(2, TimeUnit.SECONDS);
            if (!tenantAcquired) throw new AiUnavailableException("AI 服务繁忙");
            globalAcquired = globalConcurrency.tryAcquire(2, TimeUnit.SECONDS);
            if (!globalAcquired) throw new AiUnavailableException("AI 服务繁忙");
            long start = System.currentTimeMillis();
            T result = call.run(cfg);
            circuit.recordSuccess();
            log.info("AI 调用成功 tenant={} scope={} model={} 耗时={}ms", tenant, scope, cfg.model,
                    System.currentTimeMillis() - start);
            return result;
        } catch (AiUnavailableException e) {
            circuit.releaseProbe();
            throw e;
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            circuit.releaseProbe();
            throw new AiUnavailableException("AI 调用被中断");
        } catch (Exception e) {
            if (shouldTrip(e)) circuit.recordFailure(); else circuit.releaseProbe();
            log.warn("AI 调用失败 tenant={} scope={}: {}", tenant, scope, safeFailureMessage(e));
            if (preserveLocalErrors && !isProviderFailure(e) && e instanceof RuntimeException) {
                throw (RuntimeException) e;
            }
            throw new AiUnavailableException(safeFailureMessage(e));
        } finally {
            if (globalAcquired) globalConcurrency.release();
            if (tenantAcquired) tenantState.concurrency.release();
        }
    }

    public Map<String, Object> ping() {
        AiConfigService.AiConfig cfg = configService.effective();
        Map<String, Object> result = new LinkedHashMap<String, Object>();
        result.put("model", cfg == null ? null : cfg.model);
        result.put("baseUrl", cfg == null ? null : cfg.baseUrl);
        if (!ready(cfg)) {
            result.put("ok", false);
            result.put("error", cfg != null && cfg.enabled ? "尚未配置 API Key" : "AI 功能已停用");
            return result;
        }
        long start = System.currentTimeMillis();
        try {
            ChatRequest req = new ChatRequest();
            req.getMessages().add(ChatMessage.system("你是连接测试助手,只输出 JSON:{\"pong\":true}"));
            req.getMessages().add(ChatMessage.user("{\"ping\":true}"));
            req.setMaxTokens(30);
            req.setJsonMode(true);
            String content = chatWithConfig("main", cfg, req).getContent();
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

    private AiConfigService.AiConfig mediaConfig(String service) {
        return "main".equals(service) ? configService.effective() : configService.mediaConfig(service);
    }

    private boolean ready(AiConfigService.AiConfig cfg) {
        return cfg != null && cfg.isReady() && nonBlank(cfg.baseUrl) && nonBlank(cfg.model);
    }

    private boolean shouldTrip(Exception error) {
        String message = String.valueOf(error.getMessage());
        Matcher status = HTTP_STATUS.matcher(message);
        if (status.find()) {
            int code = Integer.parseInt(status.group(1));
            return code == 429 || code >= 500;
        }
        return error instanceof java.net.SocketTimeoutException
                || error instanceof java.net.ConnectException
                || message.toLowerCase().contains("timeout");
    }

    private boolean isProviderFailure(Exception error) {
        String message = String.valueOf(error.getMessage()).toLowerCase();
        return HTTP_STATUS.matcher(message).find() || message.contains("timeout")
                || error instanceof java.net.SocketException || error instanceof java.net.ConnectException;
    }

    private String safeFailureMessage(Exception error) {
        String message = String.valueOf(error.getMessage());
        Matcher status = HTTP_STATUS.matcher(message);
        if (status.find()) return "AI 调用失败 HTTP " + status.group(1);
        if (error instanceof java.net.SocketTimeoutException || message.toLowerCase().contains("timeout")) {
            return "AI 调用超时，请稍后重试";
        }
        return "AI 调用失败，请检查模型能力、密钥和服务连接";
    }

    private String circuitKey(String tenant, String scope, AiConfigService.AiConfig cfg) {
        String secret = DigestUtils.md5DigestAsHex(String.valueOf(cfg.apiKey).getBytes(StandardCharsets.UTF_8));
        return tenant + "|" + (scope == null ? "main" : scope) + "|" + String.valueOf(cfg.baseUrl)
                + "|" + String.valueOf(cfg.model) + "|" + secret;
    }

    private boolean nonBlank(String value) {
        return value != null && !value.trim().isEmpty();
    }

    @FunctionalInterface
    public interface CheckedCall<T> {
        T run(AiConfigService.AiConfig cfg) throws Exception;
    }

    private static class TenantState {
        final Semaphore concurrency = new Semaphore(PER_TENANT_CONCURRENCY);
    }

    /** 原子计数 + 半开探测，避免并发失败丢计数或多个请求同时冲击已恢复服务。 */
    private static class CircuitState {
        private final AtomicInteger consecutiveFailures = new AtomicInteger();
        private volatile long openUntil;
        private boolean probeInFlight;

        synchronized boolean tryEnter() {
            long now = System.currentTimeMillis();
            if (now < openUntil) return false;
            if (openUntil > 0L && probeInFlight) return false;
            if (openUntil > 0L) probeInFlight = true;
            return true;
        }

        synchronized void recordSuccess() {
            consecutiveFailures.set(0);
            openUntil = 0L;
            probeInFlight = false;
        }

        synchronized void recordFailure() {
            boolean halfOpen = openUntil > 0L && System.currentTimeMillis() >= openUntil;
            probeInFlight = false;
            if (halfOpen) {
                openUntil = System.currentTimeMillis() + CIRCUIT_COOLDOWN_MS;
                consecutiveFailures.set(0);
                log.warn("AI 熔断探测失败,继续熔断 {} 秒", CIRCUIT_COOLDOWN_MS / 1000);
                return;
            }
            int failures = consecutiveFailures.incrementAndGet();
            if (failures >= CIRCUIT_THRESHOLD) {
                openUntil = System.currentTimeMillis() + CIRCUIT_COOLDOWN_MS;
                consecutiveFailures.set(0);
                log.warn("AI 连续失败,熔断 {} 秒", CIRCUIT_COOLDOWN_MS / 1000);
            }
        }

        synchronized void releaseProbe() {
            probeInFlight = false;
        }
    }

    public static class AiUnavailableException extends IllegalStateException {
        public AiUnavailableException(String message) {
            super(message);
        }
    }
}
