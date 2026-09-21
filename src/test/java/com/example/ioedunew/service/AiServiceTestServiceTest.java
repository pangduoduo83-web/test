package com.example.ioedunew.service;

import com.example.ioedunew.ai.llm.OpenAiCompatGateway;
import com.example.ioedunew.common.SecretCrypto;
import com.example.ioedunew.entity.SystemSetting;
import com.example.ioedunew.repository.SystemSettingRepository;
import com.example.ioedunew.tenant.TenantContext;
import com.fasterxml.jackson.databind.*;
import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.*;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.concurrent.atomic.AtomicInteger;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class AiServiceTestServiceTest {
    private final ObjectMapper json = new ObjectMapper();
    private final AtomicInteger calls = new AtomicInteger();
    private HttpServer server;
    private AiConfigService configs;
    private AiServiceTestService tests;
    private String base, path, authorization, contentType;
    private byte[] body;
    private int status = 200;
    private String response = "{\"choices\":[{\"message\":{\"content\":\"连接成功\"}}]}";
    private final Map<String, AiConfigService.AiConfig> saved = new HashMap<>();

    @BeforeEach void setup() throws Exception {
        server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/", exchange -> {
            calls.incrementAndGet();
            path = exchange.getRequestURI().getPath();
            authorization = exchange.getRequestHeaders().getFirst("Authorization");
            contentType = exchange.getRequestHeaders().getFirst("Content-Type");
            body = MineruService.readBounded(exchange.getRequestBody(), 1024 * 1024);
            byte[] bytes = response.getBytes(StandardCharsets.UTF_8);
            exchange.getResponseHeaders().set("Content-Type", "application/json");
            exchange.sendResponseHeaders(status, bytes.length);
            exchange.getResponseBody().write(bytes); exchange.close();
        });
        server.start();
        base = "http://127.0.0.1:" + server.getAddress().getPort();
        configs = mock(AiConfigService.class);
        for (String service : Arrays.asList("main", "vision", "speech", "mineruCloud", "mineruLocal")) {
            AiConfigService.AiConfig cfg = new AiConfigService.AiConfig();
            cfg.baseUrl = base; cfg.model = "saved-model"; cfg.apiKey = service + "-saved-key"; cfg.enabled = false;
            saved.put(service, cfg);
            if ("main".equals(service)) when(configs.effective()).thenReturn(cfg);
            else when(configs.mediaConfig(service)).thenReturn(cfg);
        }
        tests = create(configs);
    }

    private AiServiceTestService create(AiConfigService config) {
        OpenAiCompatGateway gateway = new OpenAiCompatGateway(json);
        ReviewModelService media = new ReviewModelService(config, gateway, json);
        return new AiServiceTestService(config, gateway, media, new MineruService(config, json, media));
    }

    @AfterEach void stop() { server.stop(0); }

    private Map<String, Object> draft(String model) {
        Map<String, Object> fields = new HashMap<>();
        fields.put("baseUrl", base); fields.put("model", model); fields.put("apiKey", "");
        return fields;
    }

    private void success(Map<String, Object> result) { assertEquals(true, result.get("ok"), result.toString()); }
    private void failure(Map<String, Object> result) { assertEquals(false, result.get("ok"), result.toString()); }

    @Test void testsUnsavedMainWithoutPersistingOrEnablingIt() throws Exception {
        Map<String, Object> fields = draft("draft-model"); fields.put("apiKey", "draft-secret");
        success(tests.test("main", fields));
        assertEquals("draft-model", json.readTree(body).path("model").asText());
        assertEquals("Bearer draft-secret", authorization);
        assertEquals("saved-model", saved.get("main").model);
        assertFalse(saved.get("main").enabled);
        verify(configs, never()).update(any());
    }

    @Test void imageProbeActuallySendsAnImageUsingOnlyVisionKey() throws Exception {
        success(tests.test("vision", draft("image-model")));
        assertEquals("Bearer vision-saved-key", authorization);
        JsonNode content = json.readTree(body).path("messages").path(0).path("content");
        assertEquals("image_url", content.path(1).path("type").asText());
        String image = content.path(1).path("image_url").path("url").asText();
        byte[] jpeg = Base64.getDecoder().decode(image.substring(image.indexOf(',') + 1));
        assertEquals(160, javax.imageio.ImageIO.read(new java.io.ByteArrayInputStream(jpeg)).getWidth());
        verify(configs, never()).effective();
    }

    @Test void qwenProbeSendsRealBundledWavToAudioChat() throws Exception {
        Map<String, Object> fields = draft("qwen3-asr-flash");
        fields.put("baseUrl", base + "/compatible-mode"); fields.put("speechProtocol", "qwen");
        success(tests.test("speech", fields));
        assertEquals("/compatible-mode/v1/chat/completions", path);
        assertEquals("Bearer speech-saved-key", authorization);
        String data = json.readTree(body).path("messages").path(0).path("content").path(0).path("input_audio").path("data").asText();
        byte[] wav = Base64.getDecoder().decode(data.substring(data.indexOf(',') + 1));
        assertEquals("RIFF", new String(wav, 0, 4, StandardCharsets.US_ASCII));
        assertTrue(wav.length > 32000 && wav.length < 200000);
        assertEquals(1, calls.get());
        verify(configs, never()).effective();
    }

    @Test void genericSpeechStillUsesMultipartTranscriptions() {
        response = "{\"text\":\"这是语音识别测试\"}";
        Map<String, Object> fields = draft("whisper-1"); fields.put("speechProtocol", "openai");
        success(tests.test("speech", fields));
        assertEquals("/audio/transcriptions", path);
        assertTrue(contentType.startsWith("multipart/form-data"));
    }

    @Test void emptyTranscriptionDoesNotPassCapabilityTest() {
        response = "{\"text\":\"\"}";
        Map<String, Object> fields = draft("whisper-1"); fields.put("speechProtocol", "openai");
        failure(tests.test("speech", fields));
    }

    @Test void rejectsUnsupportedSpeechModelBeforeCallingProvider() {
        Map<String, Object> fields = draft("qwen3-asr-flash-filetrans"); fields.put("speechProtocol", "qwen");
        failure(tests.test("speech", fields)); assertEquals(0, calls.get());
    }

    @Test void blankMediaKeyNeverFallsBackToMainKey() {
        saved.get("vision").apiKey = "";
        failure(tests.test("vision", draft("image-model")));
        assertEquals(0, calls.get()); verify(configs, never()).effective();
    }

    @ParameterizedTest @ValueSource(strings = {"https://127.0.0.1:1234", "http://localhost:1234", "http://127.0.0.1:1234"})
    void changedOriginCannotReuseStoredSecret(String url) {
        Map<String, Object> fields = draft("image-model"); fields.put("baseUrl", url);
        Map<String, Object> result = tests.test("vision", fields);
        failure(result); assertTrue(result.get("error").toString().contains("API Key"));
        assertEquals(0, calls.get());
    }

    @Test void explicitDraftKeyCanTestNewOrigin() {
        saved.get("vision").baseUrl = "https://old.example.invalid";
        Map<String, Object> fields = draft("new-model"); fields.put("apiKey", "new-secret");
        success(tests.test("vision", fields)); assertEquals("Bearer new-secret", authorization);
    }

    @ParameterizedTest @ValueSource(strings = {"file:///tmp/model", "https://user:secret@example.com", "https://example.com?key=secret", "https://example.com#bad"})
    void rejectsMalformedOrCredentialBearingBaseUrls(String url) {
        Map<String, Object> fields = draft("model"); fields.put("baseUrl", url);
        failure(tests.test("main", fields)); assertEquals(0, calls.get());
    }

    @ParameterizedTest @ValueSource(strings = {"main", "vision", "speech", "mineruCloud", "mineruLocal"})
    void providerAuthFailureIsScopedAndDoesNotExposeBody(String service) {
        status = 401; response = "{\"error\":{\"message\":\"leaked-secret-body\"}}";
        Map<String, Object> fields = draft("qwen3-asr-flash"); fields.put("speechProtocol", "qwen");
        Map<String, Object> result = tests.test(service, fields);
        failure(result); assertEquals(service, result.get("service"));
        assertTrue(result.get("error").toString().contains("HTTP 401"));
        assertFalse(result.toString().contains("leaked-secret"));
        assertFalse(result.toString().contains("saved-key"));
    }

    @Test void cloudProbeValidatesUploadGrantWithoutSendingDocument() throws Exception {
        response = "{\"code\":0,\"data\":{\"batch_id\":\"test-batch\",\"file_urls\":[\"https://upload.example.invalid/secret\"]}}";
        Map<String, Object> result = tests.test("mineruCloud", draft("vlm"));
        success(result); assertEquals("/api/v4/file-urls/batch", path);
        assertEquals("vlm", json.readTree(body).path("model_version").asText());
        assertEquals("ioedu-connection-test.pdf", json.readTree(body).path("files").path(0).path("name").asText());
        assertEquals(1, calls.get()); assertFalse(result.toString().contains("upload.example"));
        assertTrue(result.get("summary").toString().contains("未执行文档解析"));
    }

    @ParameterizedTest @ValueSource(strings = {"{}", "{\"code\":-1}", "{\"code\":0,\"data\":{}}"})
    void cloudDoesNotTreatAnyHttp200AsSuccess(String invalid) {
        response = invalid; failure(tests.test("mineruCloud", draft("vlm")));
    }

    @Test void anonymousSelfhostChecksV1ZipCapability() {
        response = "{\"features\":{\"output_formats\":[\"zip\",\"markdown\"]}}";
        saved.get("mineruLocal").apiKey = "";
        success(tests.test("mineruLocal", draft("standard")));
        assertEquals("/v1/health", path); assertNull(authorization); assertEquals(1, calls.get());
    }

    @Test void selfhostMissingZipIsNotPassed() {
        response = "{\"features\":{\"output_formats\":[\"markdown\"]}}";
        failure(tests.test("mineruLocal", draft("standard")));
    }

    @Test void unknownServiceDoesNotResolveAnySecret() {
        failure(tests.test("other", draft("model"))); verifyNoInteractions(configs);
    }

    @Test void exactCredentialEchoIsRedacted() {
        response = "{\"choices\":[{\"message\":{\"content\":\"main-saved-key\"}}]}";
        Map<String, Object> result = tests.test("main", draft("model"));
        success(result); assertFalse(result.toString().contains("main-saved-key"));
    }

    @Test void savedCredentialsFollowCurrentTenantAndTestsNeverWriteSettings() {
        SystemSettingRepository repository = mock(SystemSettingRepository.class);
        when(repository.findAll()).thenAnswer(call -> {
            List<SystemSetting> rows = new ArrayList<>();
            for (String[] field : new String[][]{{"BaseUrl", base}, {"ApiKey", TenantContext.require() + "-key"}, {"Model", "saved-model"}}) {
                SystemSetting row = new SystemSetting(); row.setSettingKey("ai.vision" + field[0]); row.setSettingValue(field[1]); rows.add(row);
            }
            return rows;
        });
        AiServiceTestService isolated = create(new AiConfigService(repository, new SecretCrypto("test-master")));
        TenantContext.runAs("tenant-a", () -> { success(isolated.test("vision", draft("image-model"))); assertEquals("Bearer tenant-a-key", authorization); });
        TenantContext.runAs("tenant-b", () -> { success(isolated.test("vision", draft("image-model"))); assertEquals("Bearer tenant-b-key", authorization); });
        verify(repository, never()).save(any()); assertNull(TenantContext.get());
    }
}
