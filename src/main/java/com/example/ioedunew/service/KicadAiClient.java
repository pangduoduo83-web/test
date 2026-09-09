package com.example.ioedunew.service;

import com.example.ioedunew.tenant.TenantContext;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.ResponseEntity;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * KiCad AI 设计助手(独立 Python 服务)的内网客户端:目前只拉当前站点的用量快照给数据大屏 / 管理端。
 * 未配置地址或服务不可达时返回 null,调用方按"未接入"展示,不影响主流程。
 */
@Service
public class KicadAiClient {

    private static final Logger log = LoggerFactory.getLogger(KicadAiClient.class);

    @Value("${ioedu.kicad-ai.url:}")
    private String baseUrl;

    @Value("${ioedu.kicad-ai.internal-token:}")
    private String internalToken;

    private final ObjectMapper om;
    private final RestTemplate rest;

    public KicadAiClient(ObjectMapper om) {
        this.om = om;
        SimpleClientHttpRequestFactory f = new SimpleClientHttpRequestFactory();
        f.setConnectTimeout(1500);
        f.setReadTimeout(3000);
        this.rest = new RestTemplate(f);
    }

    public boolean enabled() {
        return baseUrl != null && !baseUrl.trim().isEmpty();
    }

    /** 当前站点在 KiCad 助手里的用量:用户、对话、工具调用、Token、在线人数;不可用返回 null */
    public Map<String, Object> tenantStats() {
        if (!enabled()) {
            return null;
        }
        String tenant = TenantContext.get() == null ? "default" : TenantContext.get();
        try {
            HttpHeaders h = new HttpHeaders();
            h.set("X-Internal-Token", internalToken == null ? "" : internalToken);
            ResponseEntity<String> resp = rest.exchange(baseUrl.replaceAll("/+$", "") + "/api/internal/stats?tenant=" + tenant,
                    HttpMethod.GET, new HttpEntity<>(h), String.class);
            JsonNode n = om.readTree(resp.getBody());
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("users", n.path("users").asInt());
            m.put("activeUsers24h", n.path("activeUsers24h").asInt());
            m.put("projects", n.path("projects").asInt());
            m.put("conversations", n.path("conversations").asInt());
            m.put("conversationsActive24h", n.path("conversationsActive24h").asInt());
            m.put("messages", n.path("messages").asInt());
            m.put("toolCalls", n.path("toolCalls").asInt());
            m.put("tokens", n.path("inputTokens").asLong() + n.path("outputTokens").asLong());
            m.put("online", n.path("online").asInt());
            m.put("activeRuns", n.path("activeRuns").asInt());
            m.put("agentReady", n.path("agentReady").asBoolean(false));
            m.put("toolCount", n.path("toolCount").asInt());
            m.put("model", n.path("model").asText(null));
            return m;
        } catch (Exception e) {
            log.debug("KiCad AI 用量拉取失败: {}", e.getMessage());
            return null;
        }
    }
}
