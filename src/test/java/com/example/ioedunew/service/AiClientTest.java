package com.example.ioedunew.service;

import com.example.ioedunew.ai.llm.ChatRequest;
import com.example.ioedunew.ai.llm.ChatResult;
import com.example.ioedunew.ai.llm.LlmGateway;
import com.example.ioedunew.tenant.TenantContext;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class AiClientTest {
    private final AiConfigService configs = mock(AiConfigService.class);
    private final LlmGateway gateway = mock(LlmGateway.class);
    private final AiConfigService.AiConfig main = config("main-model");
    private final AiConfigService.AiConfig vision = config("vision-model");
    private AiClient client;

    @BeforeEach
    void setUp() {
        TenantContext.set("tenant-ai-client-test");
        when(configs.effective()).thenReturn(main);
        when(configs.mediaConfig("vision")).thenReturn(vision);
        client = new AiClient(configs, gateway);
    }

    @AfterEach
    void clearTenant() {
        TenantContext.clear();
    }

    @Test
    void visionFailuresDoNotOpenTheMainModelCircuit() throws Exception {
        doAnswer(invocation -> { throw new IllegalStateException("模型接口 HTTP 500"); })
                .when(gateway).chat(eq(vision), any(ChatRequest.class));
        ChatResult success = new ChatResult();
        success.setContent("ok");
        when(gateway.chat(eq(main), any(ChatRequest.class))).thenReturn(success);

        for (int i = 0; i < 3; i++) {
            assertThrows(AiClient.AiUnavailableException.class,
                    () -> client.chat("vision", new ChatRequest()));
        }
        assertEquals("ok", client.chat(new ChatRequest()).getContent());
    }

    @Test
    void providerConfigurationErrorsDoNotTripTheCircuit() throws Exception {
        AtomicInteger calls = new AtomicInteger();
        doAnswer(invocation -> {
            calls.incrementAndGet();
            throw new IllegalStateException("模型接口 HTTP 401");
        }).when(gateway).chat(eq(vision), any(ChatRequest.class));

        for (int i = 0; i < 4; i++) {
            assertThrows(AiClient.AiUnavailableException.class,
                    () -> client.chat("vision", new ChatRequest()));
        }
        assertEquals(4, calls.get());
    }

    private static AiConfigService.AiConfig config(String model) {
        AiConfigService.AiConfig cfg = new AiConfigService.AiConfig();
        cfg.enabled = true;
        cfg.baseUrl = "https://models.example.test/v1";
        cfg.model = model;
        cfg.apiKey = model + "-key";
        return cfg;
    }
}
