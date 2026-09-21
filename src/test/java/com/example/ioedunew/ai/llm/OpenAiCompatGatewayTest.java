package com.example.ioedunew.ai.llm;

import com.example.ioedunew.service.AiConfigService;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.*;

class OpenAiCompatGatewayTest {
    private final ObjectMapper json = new ObjectMapper();
    private HttpServer server;
    private OpenAiCompatGateway gateway;
    private AiConfigService.AiConfig config;
    private String host;
    private volatile String expectedPath, actualPath, authorization;
    private volatile JsonNode requestBody;

    @BeforeEach void setUp() throws Exception {
        gateway = new OpenAiCompatGateway(json);
        server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/", exchange -> {
            actualPath = exchange.getRequestURI().getPath();
            authorization = exchange.getRequestHeaders().getFirst("Authorization");
            requestBody = json.readTree(exchange.getRequestBody());
            boolean found = actualPath.equals(expectedPath);
            boolean streaming = requestBody.path("stream").asBoolean();
            String response = !found ? "" : streaming
                    ? "data: {\"choices\":[{\"delta\":{\"content\":\"ok\"},\"finish_reason\":\"stop\"}]}\n\ndata: [DONE]\n\n"
                    : "{\"choices\":[{\"message\":{\"content\":\"ok\"},\"finish_reason\":\"stop\"}]}";
            byte[] bytes = response.getBytes(StandardCharsets.UTF_8);
            exchange.getResponseHeaders().set("Content-Type", streaming ? "text/event-stream" : "application/json");
            exchange.sendResponseHeaders(found ? 200 : 404, found ? bytes.length : -1);
            if (found) exchange.getResponseBody().write(bytes);
            exchange.close();
        });
        server.start();
        host = "http://127.0.0.1:" + server.getAddress().getPort();
        config = new AiConfigService.AiConfig();
        config.baseUrl = host + "/compatible-mode";
        config.model = "qwen-plus";
        config.apiKey = "test-only-key";
        config.maxTokens = 100;
        config.connectTimeoutMs = 1000;
        config.readTimeoutMs = 1000;
        expectedPath = "/compatible-mode/v1/chat/completions";
    }

    @AfterEach void tearDown() { if (server != null) server.stop(0); }

    @ParameterizedTest
    @CsvSource({
            "/compatible-mode, /compatible-mode/v1/chat/completions",
            "/compatible-mode/, /compatible-mode/v1/chat/completions",
            "/compatible-mode/v1, /compatible-mode/v1/chat/completions",
            "/compatible-mode/v1/, /compatible-mode/v1/chat/completions",
            "/v1, /v1/chat/completions",
            "'', /chat/completions",
            "/proxy/v1, /proxy/v1/chat/completions",
            "/compatible-mode-custom, /compatible-mode-custom/chat/completions"
    })
    void acceptsLegacyQwenAddressWithoutChangingOtherProviders(String suffix, String path) throws Exception {
        config.baseUrl = host + suffix;
        expectedPath = path;
        ChatRequest request = new ChatRequest();
        request.getMessages().add(ChatMessage.user("test"));
        assertEquals("ok", gateway.chat(config, request).getContent());
        assertEquals(path, actualPath);
        assertEquals("Bearer test-only-key", authorization);
        assertEquals("qwen-plus", requestBody.path("model").asText());
        assertFalse(requestBody.path("stream").asBoolean());
    }

    @Test void streamingUsesTheSameCorrectedEndpoint() throws Exception {
        ChatRequest request = new ChatRequest();
        request.getMessages().add(ChatMessage.user("test"));
        StringBuilder text = new StringBuilder();
        AtomicReference<ChatResult> complete = new AtomicReference<>();
        gateway.stream(config, request, new StreamListener() {
            @Override public void onContent(String delta) { text.append(delta); }
            @Override public void onComplete(ChatResult result) { complete.set(result); }
        });
        assertEquals(expectedPath, actualPath);
        assertTrue(requestBody.path("stream").asBoolean());
        assertEquals("ok", text.toString());
        assertNotNull(complete.get());
        assertEquals("ok", complete.get().getContent());
    }

    @Test void imageRecognitionUsesTheSameCorrectedEndpoint() throws Exception {
        config.model = "qwen3.8-max";
        ChatRequest request = new ChatRequest();
        ChatMessage message = ChatMessage.user("Describe the image");
        message.getImageUrls().add("data:image/png;base64,AA==");
        request.getMessages().add(message);
        assertEquals("ok", gateway.chat(config, request).getContent());
        assertEquals(expectedPath, actualPath);
        JsonNode content = requestBody.path("messages").path(0).path("content");
        assertEquals("image_url", content.path(1).path("type").asText());
        assertEquals("data:image/png;base64,AA==", content.path(1).path("image_url").path("url").asText());
    }
}
