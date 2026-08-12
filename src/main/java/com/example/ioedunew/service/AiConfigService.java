package com.example.ioedunew.service;

import com.example.ioedunew.common.BusinessException;
import com.example.ioedunew.entity.SystemSetting;
import com.example.ioedunew.repository.SystemSettingRepository;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * AI 模型配置服务:数据库设置优先,环境变量/配置文件作为默认值。
 * 管理后台修改后立即生效,无需重启;api-key 对外只回显掩码。
 */
@Service
public class AiConfigService {

    private static final String KEY_ENABLED = "ai.enabled";
    private static final String KEY_BASE_URL = "ai.baseUrl";
    private static final String KEY_API_KEY = "ai.apiKey";
    private static final String KEY_MODEL = "ai.model";
    private static final String KEY_MAX_TOKENS = "ai.maxTokens";
    private static final String KEY_TEMPERATURE = "ai.temperature";
    private static final String KEY_CONNECT_TIMEOUT = "ai.connectTimeoutMs";
    private static final String KEY_READ_TIMEOUT = "ai.readTimeoutMs";

    private final SystemSettingRepository repository;

    @Value("${ioedu.ai.base-url}")
    private String envBaseUrl;

    @Value("${ioedu.ai.api-key}")
    private String envApiKey;

    @Value("${ioedu.ai.model}")
    private String envModel;

    @Value("${ioedu.ai.connect-timeout-ms}")
    private int envConnectTimeoutMs;

    @Value("${ioedu.ai.read-timeout-ms}")
    private int envReadTimeoutMs;

    public AiConfigService(SystemSettingRepository repository) {
        this.repository = repository;
    }

    /** 当前生效配置(DB 优先,env 兜底) */
    public AiConfig effective() {
        Map<String, String> db = loadAll();
        AiConfig cfg = new AiConfig();
        cfg.baseUrl = firstNonBlank(db.get(KEY_BASE_URL), envBaseUrl);
        cfg.apiKey = firstNonBlank(db.get(KEY_API_KEY), envApiKey);
        cfg.apiKeySource = isBlank(db.get(KEY_API_KEY)) ? (isBlank(envApiKey) ? "NONE" : "ENV") : "DB";
        cfg.model = firstNonBlank(db.get(KEY_MODEL), envModel);
        cfg.maxTokens = parseInt(db.get(KEY_MAX_TOKENS), 2000);
        cfg.temperature = parseDouble(db.get(KEY_TEMPERATURE), 0.4);
        cfg.connectTimeoutMs = parseInt(db.get(KEY_CONNECT_TIMEOUT), envConnectTimeoutMs);
        cfg.readTimeoutMs = parseInt(db.get(KEY_READ_TIMEOUT), envReadTimeoutMs);
        String enabled = db.get(KEY_ENABLED);
        cfg.enabled = enabled == null || "true".equalsIgnoreCase(enabled);
        return cfg;
    }

    /** 管理后台回显视图(密钥掩码) */
    public Map<String, Object> view() {
        AiConfig cfg = effective();
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("enabled", cfg.enabled);
        m.put("baseUrl", cfg.baseUrl);
        m.put("model", cfg.model);
        m.put("apiKeySet", !isBlank(cfg.apiKey));
        m.put("apiKeyMasked", mask(cfg.apiKey));
        m.put("apiKeySource", cfg.apiKeySource);
        m.put("maxTokens", cfg.maxTokens);
        m.put("temperature", cfg.temperature);
        m.put("connectTimeoutMs", cfg.connectTimeoutMs);
        m.put("readTimeoutMs", cfg.readTimeoutMs);
        return m;
    }

    /**
     * 更新配置:仅更新传入的字段;apiKey 传空串或不传表示保持不变。
     */
    @Transactional
    public Map<String, Object> update(Map<String, Object> body) {
        if (body == null || body.isEmpty()) {
            throw new BusinessException("没有需要保存的配置");
        }
        if (body.containsKey("enabled")) {
            put(KEY_ENABLED, String.valueOf(Boolean.TRUE.equals(body.get("enabled"))));
        }
        if (body.get("baseUrl") != null) {
            String v = String.valueOf(body.get("baseUrl")).trim();
            if (!v.startsWith("http://") && !v.startsWith("https://")) {
                throw new BusinessException("接口地址必须以 http:// 或 https:// 开头");
            }
            put(KEY_BASE_URL, v.replaceAll("/+$", ""));
        }
        if (body.get("model") != null) {
            String v = String.valueOf(body.get("model")).trim();
            if (v.isEmpty()) {
                throw new BusinessException("模型名称不能为空");
            }
            put(KEY_MODEL, v);
        }
        if (body.get("apiKey") != null) {
            String v = String.valueOf(body.get("apiKey")).trim();
            if (!v.isEmpty()) {
                put(KEY_API_KEY, v);
            }
        }
        if (body.get("maxTokens") != null) {
            int v = toInt(body.get("maxTokens"), "输出 Token 上限");
            if (v < 200 || v > 8000) {
                throw new BusinessException("输出 Token 上限需在 200~8000 之间");
            }
            put(KEY_MAX_TOKENS, String.valueOf(v));
        }
        if (body.get("temperature") != null) {
            double v;
            try {
                v = Double.parseDouble(String.valueOf(body.get("temperature")));
            } catch (NumberFormatException e) {
                throw new BusinessException("温度参数格式不正确");
            }
            if (v < 0 || v > 2) {
                throw new BusinessException("温度参数需在 0~2 之间");
            }
            put(KEY_TEMPERATURE, String.valueOf(v));
        }
        if (body.get("connectTimeoutMs") != null) {
            int v = toInt(body.get("connectTimeoutMs"), "连接超时");
            if (v < 1000 || v > 30000) {
                throw new BusinessException("连接超时需在 1000~30000 毫秒之间");
            }
            put(KEY_CONNECT_TIMEOUT, String.valueOf(v));
        }
        if (body.get("readTimeoutMs") != null) {
            int v = toInt(body.get("readTimeoutMs"), "读取超时");
            if (v < 3000 || v > 120000) {
                throw new BusinessException("读取超时需在 3000~120000 毫秒之间");
            }
            put(KEY_READ_TIMEOUT, String.valueOf(v));
        }
        return view();
    }

    // ---------- 内部工具 ----------

    private Map<String, String> loadAll() {
        Map<String, String> map = new HashMap<>();
        List<SystemSetting> all = repository.findAll();
        for (SystemSetting s : all) {
            if (s.getSettingKey() != null && s.getSettingKey().startsWith("ai.")) {
                map.put(s.getSettingKey(), s.getSettingValue());
            }
        }
        return map;
    }

    private void put(String key, String value) {
        SystemSetting s = repository.findById(key).orElseGet(() -> {
            SystemSetting n = new SystemSetting();
            n.setSettingKey(key);
            return n;
        });
        s.setSettingValue(value);
        s.setUpdatedAt(LocalDateTime.now());
        repository.save(s);
    }

    private String mask(String key) {
        if (isBlank(key)) {
            return "";
        }
        String k = key.trim();
        if (k.length() <= 8) {
            return "****";
        }
        return k.substring(0, 4) + "****" + k.substring(k.length() - 4);
    }

    private int toInt(Object v, String label) {
        try {
            return (int) Double.parseDouble(String.valueOf(v));
        } catch (NumberFormatException e) {
            throw new BusinessException(label + "格式不正确");
        }
    }

    private String firstNonBlank(String a, String b) {
        return isBlank(a) ? (b == null ? "" : b.trim()) : a.trim();
    }

    private boolean isBlank(String v) {
        return v == null || v.trim().isEmpty();
    }

    private int parseInt(String v, int def) {
        try {
            return isBlank(v) ? def : Integer.parseInt(v.trim());
        } catch (NumberFormatException e) {
            return def;
        }
    }

    private double parseDouble(String v, double def) {
        try {
            return isBlank(v) ? def : Double.parseDouble(v.trim());
        } catch (NumberFormatException e) {
            return def;
        }
    }

    /** 生效配置模型 */
    public static class AiConfig {
        public boolean enabled;
        public String baseUrl;
        public String apiKey;
        public String apiKeySource;
        public String model;
        public int maxTokens;
        public double temperature;
        public int connectTimeoutMs;
        public int readTimeoutMs;

        public boolean isReady() {
            return enabled && apiKey != null && !apiKey.trim().isEmpty();
        }
    }
}
