package com.example.ioedunew.service;

import com.example.ioedunew.ai.llm.*;
import com.example.ioedunew.common.BusinessException;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Service;
import javax.imageio.ImageIO;
import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.net.URI;
import java.net.SocketTimeoutException;
import java.nio.file.*;
import java.util.*;
import java.util.regex.*;

/** Small capability probes using the current tenant's independent service credentials. */
@Service
public class AiServiceTestService {
    private static final Pattern HTTP_STATUS = Pattern.compile("HTTP (\\d{3})\\b");
    private final AiConfigService configs;
    private final LlmGateway gateway;
    private final AiClient aiClient;
    private final ReviewModelService media;
    private final MineruService mineru;

    public AiServiceTestService(AiConfigService configs, LlmGateway gateway,
                                ReviewModelService media, MineruService mineru) {
        this(configs, gateway, media, mineru, new AiClient(configs, gateway));
    }

    @org.springframework.beans.factory.annotation.Autowired
    public AiServiceTestService(AiConfigService configs, LlmGateway gateway,
                                ReviewModelService media, MineruService mineru, AiClient aiClient) {
        this.configs = configs; this.gateway = gateway; this.aiClient = aiClient; this.media = media; this.mineru = mineru;
    }

    public Map<String, Object> test(String service, Map<String, Object> draft) {
        long start = System.nanoTime();
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("service", service);
        try {
            AiConfigService.AiConfig cfg = configuration(service, draft);
            String reply;
            String summary;
            if ("speech".equals(service)) {
                Path audio = Files.createTempFile("ioedu-asr-test-", ".wav");
                try {
                    try (InputStream input = new ClassPathResource("ai-test/speech.wav").getInputStream()) {
                        Files.copy(input, audio, StandardCopyOption.REPLACE_EXISTING);
                    }
                    reply = media.transcribe(audio, cfg);
                } finally { Files.deleteIfExists(audio); }
                summary = "语音接口已返回测试音频的转写结果";
            } else if (service.startsWith("mineru")) {
                summary = mineru.testConnection(cfg, "mineruCloud".equals(service));
                reply = "";
            } else {
                ChatRequest request = new ChatRequest();
                request.setMaxTokens(256);
                ChatMessage message = ChatMessage.user("请用一句简短的中文回复：连接测试成功。");
                if ("vision".equals(service)) {
                    message = ChatMessage.user("请用一句话描述图片中的形状和颜色。");
                    message.getImageUrls().add("data:image/jpeg;base64," + Base64.getEncoder().encodeToString(testImage()));
                }
                request.getMessages().add(message);
                reply = aiClient.chatWithConfig("probe:" + service, cfg, request).getContent();
                summary = "vision".equals(service) ? "图片接口已返回测试图片的识别结果" : "主模型已返回测试回复";
            }
            if (!service.startsWith("mineru") && (reply == null || reply.trim().isEmpty()))
                throw new BusinessException("接口返回了空结果，请检查模型是否支持该能力后重试");
            result.put("ok", true);
            result.put("summary", summary);
            // Never return provider errors or echo an exact credential even if an upstream service does.
            reply = reply == null ? "" : reply.trim();
            if (!cfg.apiKey.isEmpty()) reply = reply.replace(cfg.apiKey, "[已隐藏密钥]");
            result.put("reply", reply.length() > 400 ? reply.substring(0, 400) + "…" : reply);
        } catch (BusinessException e) {
            result.put("ok", false); result.put("error", e.getMessage());
        } catch (Exception e) {
            result.put("ok", false); result.put("error", failure(e));
        }
        result.put("latencyMs", (System.nanoTime() - start) / 1000000);
        return result;
    }

    private AiConfigService.AiConfig configuration(String service, Map<String, Object> draft) {
        if (!Arrays.asList("main", "vision", "speech", "mineruCloud", "mineruLocal").contains(service))
            throw new BusinessException("不支持的测试服务");
        if (draft == null) throw new BusinessException("请填写待测试的服务配置");
        AiConfigService.AiConfig saved = "main".equals(service) ? configs.effective() : configs.mediaConfig(service);
        AiConfigService.AiConfig cfg = new AiConfigService.AiConfig();
        cfg.baseUrl = text(draft, "baseUrl", 2048).replaceAll("/+$", "");
        URI target = address(cfg.baseUrl);
        cfg.model = text(draft, "model", 200);
        if (cfg.model.isEmpty()) throw new BusinessException("请填写模型名称或解析版本");
        cfg.apiKey = text(draft, "apiKey", 4096);
        if (cfg.apiKey.isEmpty() && saved.apiKey != null && !saved.apiKey.trim().isEmpty()) {
            if (!MineruService.sameOrigin(address(saved.baseUrl), target))
                throw new BusinessException("服务域名或端口已改变，请填写对应的 API Key 后再测试");
            cfg.apiKey = saved.apiKey;
        }
        if (cfg.apiKey.isEmpty() && !"mineruLocal".equals(service))
            throw new BusinessException("请填写本服务的 API Key；留空仅使用本服务已保存的密钥");
        cfg.speechProtocol = "speech".equals(service) ? text(draft, "speechProtocol", 30) : "openai";
        if ("speech".equals(service)) {
            if (!Arrays.asList("openai", "qwen").contains(cfg.speechProtocol))
                throw new BusinessException("请选择语音接入方式");
            if ("qwen".equals(cfg.speechProtocol) && !cfg.model.matches("qwen3-asr-flash(?:-\\d{4}-\\d{2}-\\d{2})?"))
                throw new BusinessException("Qwen ASR 请选择 qwen3-asr-flash 或其日期版本；实时和 filetrans 型号不适用");
        }
        cfg.enabled = true; // Only this detached test config is enabled; no settings are persisted.
        cfg.maxTokens = 256; cfg.temperature = 0.1;
        cfg.connectTimeoutMs = 15000; cfg.readTimeoutMs = 120000;
        return cfg;
    }

    private static URI address(String value) {
        try {
            URI uri = new URI(value);
            if (!Arrays.asList("http", "https").contains(uri.getScheme()) || uri.getHost() == null
                    || uri.getUserInfo() != null || uri.getQuery() != null || uri.getFragment() != null)
                throw new IllegalArgumentException();
            return uri;
        } catch (Exception e) { throw new BusinessException("请填写有效的 HTTP/HTTPS 服务基础地址，不要包含密钥或查询参数"); }
    }

    private static String text(Map<String, Object> draft, String field, int max) {
        Object raw = draft.get(field);
        if (raw != null && !(raw instanceof String)) throw new BusinessException("服务配置格式不正确");
        String value = raw == null ? "" : ((String) raw).trim();
        if (value.length() > max || value.contains("\r") || value.contains("\n"))
            throw new BusinessException("服务配置过长或含有换行，请检查输入");
        return value;
    }

    private static String failure(Exception error) {
        for (Throwable cause = error; cause != null; cause = cause.getCause()) {
            Matcher status = HTTP_STATUS.matcher(String.valueOf(cause.getMessage()));
            if (status.find()) {
                String code = status.group(1);
                String hint = "401".equals(code) || "403".equals(code) ? "请检查本服务密钥、所属地域和调用权限"
                        : "404".equals(code) ? "请检查服务基础地址和模型名称，千问兼容地址应包含 /compatible-mode/v1"
                        : "429".equals(code) ? "调用受限，请检查额度或稍后重试" : "请检查模型能力、服务配置和服务商状态";
                return "服务接口 HTTP " + code + "：" + hint;
            }
            if (cause instanceof SocketTimeoutException) return "服务响应超时，请稍后重试或检查服务连接";
        }
        return "测试未通过，请检查服务连接、模型能力与密钥；文档服务还需检查解析版本和可用能力";
    }

    private static byte[] testImage() throws Exception {
        BufferedImage image = new BufferedImage(160, 120, BufferedImage.TYPE_INT_RGB);
        Graphics2D graphics = image.createGraphics();
        try {
            graphics.setColor(Color.WHITE); graphics.fillRect(0, 0, 160, 120);
            graphics.setColor(Color.RED); graphics.fillRect(20, 30, 60, 60);
            graphics.setColor(Color.BLUE); graphics.fillOval(100, 40, 40, 40);
        } finally { graphics.dispose(); }
        ByteArrayOutputStream output = new ByteArrayOutputStream();
        ImageIO.write(image, "jpeg", output);
        return output.toByteArray();
    }
}
