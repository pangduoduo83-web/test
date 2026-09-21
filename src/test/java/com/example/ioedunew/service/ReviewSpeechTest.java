package com.example.ioedunew.service;

import com.example.ioedunew.ai.llm.LlmGateway;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.io.RandomAccessFile;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Base64;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class ReviewSpeechTest {
    @TempDir Path temp;
    private final ObjectMapper json = new ObjectMapper();
    private final AtomicInteger requests = new AtomicInteger();
    private HttpServer server;
    private AiConfigService.AiConfig speech;
    private ReviewModelService service;
    private Path wav;
    private volatile String requestPath, contentType, authorization;
    private volatile byte[] requestBody;
    private String response = "{\"choices\":[{\"finish_reason\":\"stop\",\"message\":{\"content\":\"输出电压三点三伏。\"}}]}";
    private int responseStatus = 200;

    @BeforeEach void setUp() throws Exception {
        server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/", exchange -> {
            requests.incrementAndGet();
            requestPath = exchange.getRequestURI().getPath();
            contentType = exchange.getRequestHeaders().getFirst("Content-Type");
            authorization = exchange.getRequestHeaders().getFirst("Authorization");
            requestBody = MineruService.readBounded(exchange.getRequestBody(), 11L * 1024 * 1024);
            byte[] bytes = response.getBytes(StandardCharsets.UTF_8);
            exchange.getResponseHeaders().set("Content-Type", "application/json; charset=UTF-8");
            exchange.sendResponseHeaders(responseStatus, bytes.length);
            exchange.getResponseBody().write(bytes);
            exchange.close();
        });
        server.start();
        speech = new AiConfigService.AiConfig();
        speech.enabled = true;
        speech.apiKey = "test-speech-secret";
        speech.baseUrl = "http://127.0.0.1:" + server.getAddress().getPort() + "/compatible-mode/v1/";
        speech.model = "qwen3-asr-flash";
        speech.speechProtocol = "qwen";
        AiConfigService configs = mock(AiConfigService.class);
        when(configs.mediaConfig("speech")).thenReturn(speech);
        when(configs.mediaConfig("vision")).thenReturn(new AiConfigService.AiConfig());
        service = new ReviewModelService(configs, mock(LlmGateway.class), json);
        wav = temp.resolve("speech.wav");
        // The adapter transports WAV bytes unchanged; decoding is performed upstream by FFmpeg.
        Files.write(wav, new byte[]{'R', 'I', 'F', 'F', 0, 1, 2, -1, 'W', 'A', 'V', 'E'});
    }

    @AfterEach void tearDown() { if (server != null) server.stop(0); }

    @ParameterizedTest
    @ValueSource(strings = {"qwen3-asr-flash", "qwen3-asr-flash-2026-02-10"})
    void qwenUsesAudioChatAndReadsTranscription(String model) throws Exception {
        speech.model = model;
        assertEquals("输出电压三点三伏。", service.transcribe(wav));
        assertEquals("/compatible-mode/v1/chat/completions", requestPath);
        assertTrue(contentType.startsWith("application/json"));
        assertEquals("Bearer test-speech-secret", authorization);
        JsonNode body = json.readTree(requestBody);
        assertEquals(model, body.path("model").asText());
        assertFalse(body.path("stream").asBoolean(true));
        assertEquals(1, body.path("messages").size());
        JsonNode message = body.path("messages").get(0);
        assertEquals("user", message.path("role").asText());
        assertEquals(1, message.path("content").size());
        JsonNode audio = message.path("content").get(0);
        assertEquals("input_audio", audio.path("type").asText());
        assertEquals("data:audio/wav;base64," + Base64.getEncoder().encodeToString(Files.readAllBytes(wav)),
                audio.path("input_audio").path("data").asText());
        assertFalse(body.has("response_format"));
        assertEquals(1, requests.get());
    }

    @Test void qwenAlsoAcceptsCompatibleModeBaseWithoutV1() throws Exception {
        speech.baseUrl = speech.baseUrl.replace("/v1/", "/");
        service.transcribe(wav);
        assertEquals("/compatible-mode/v1/chat/completions", requestPath);
    }

    @Test void existingTranscriptionServicesRetainMultipartProtocol() throws Exception {
        speech.speechProtocol = "openai";
        speech.model = "whisper-1";
        response = "{\"text\":\"原有服务仍可使用。\"}";
        assertEquals("原有服务仍可使用。", service.transcribe(wav));
        assertEquals("/compatible-mode/v1/audio/transcriptions", requestPath);
        assertTrue(contentType.startsWith("multipart/form-data"));
        String body = new String(requestBody, StandardCharsets.ISO_8859_1);
        assertTrue(body.contains("name=\"model\""));
        assertTrue(body.contains("whisper-1"));
        assertTrue(body.contains("name=\"response_format\""));
        assertTrue(body.contains("name=\"file\"; filename=\"speech.wav\""));
        assertTrue(body.contains(new String(Files.readAllBytes(wav), StandardCharsets.ISO_8859_1)));
        assertEquals("Bearer test-speech-secret", authorization);
    }

    @Test void silenceIsAValidEmptyTranscript() throws Exception {
        response = "{\"choices\":[{\"finish_reason\":\"stop\",\"message\":{\"content\":\"\"}}]}";
        assertEquals("", service.transcribe(wav));
    }

    @ParameterizedTest
    @ValueSource(strings = {"{}", "{\"text\":\"wrong protocol\"}",
            "{\"choices\":[{\"message\":{\"content\":null}}]}",
            "{\"choices\":[{\"finish_reason\":\"length\",\"message\":{\"content\":\"incomplete\"}}]}"})
    void malformedOrTruncatedRepliesAreNotUsedAsEvidence(String reply) {
        response = reply;
        IllegalStateException error = assertThrows(IllegalStateException.class, () -> service.transcribe(wav));
        assertTrue(error.getMessage().contains("语音转文字调用失败"));
        assertEquals(1, requests.get());
    }

    @Test void serviceErrorsDoNotLeakCredentialsOrRetryAnotherProtocol() {
        responseStatus = 401;
        response = "{\"error\":{\"message\":\"test-speech-secret\"}}";
        IllegalStateException error = assertThrows(IllegalStateException.class, () -> service.transcribe(wav));
        assertFalse(error.toString().contains(speech.apiKey));
        assertNull(error.getCause());
        assertEquals(1, requests.get());
    }

    @ParameterizedTest
    @ValueSource(strings = {"qwen3-asr-flash-filetrans", "qwen3-asr-flash-realtime", "qwen-plus"})
    void unsupportedModelModesAreRejectedBeforeRequest(String model) {
        speech.model = model;
        IllegalStateException error = assertThrows(IllegalStateException.class, () -> service.transcribe(wav));
        assertTrue(error.getMessage().contains("qwen3-asr-flash"));
        assertEquals(0, requests.get());
    }

    @Test void oversizedAudioIsRejectedBeforeBase64Encoding() throws Exception {
        try (RandomAccessFile file = new RandomAccessFile(wav.toFile(), "rw")) { file.setLength(7L * 1024 * 1024 + 1); }
        IllegalStateException error = assertThrows(IllegalStateException.class, () -> service.transcribe(wav));
        assertTrue(error.getMessage().contains("7MB"));
        assertEquals(0, requests.get());
    }

    @Test void changingProtocolInvalidatesCachedEvidence() {
        speech.speechProtocol = "openai";
        String original = service.fingerprint();
        speech.speechProtocol = "qwen";
        assertNotEquals(original, service.fingerprint());
        speech.speechProtocol = "openai";
        assertEquals(original, service.fingerprint());
    }
}
