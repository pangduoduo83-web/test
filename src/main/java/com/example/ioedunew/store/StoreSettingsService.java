package com.example.ioedunew.store;

import com.example.ioedunew.common.BusinessException;
import com.example.ioedunew.common.SecretCrypto;
import com.example.ioedunew.entity.SystemSetting;
import com.example.ioedunew.repository.SystemSettingRepository;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * 项目商店接入配置(每个租户各自一份,存 system_settings):
 * 商店地址默认取平台环境变量 IOEDU_HUB_BASE_URL(所有客户共用同一个商店),API Key 由平台管理员在商店后台为该客户签发,
 * 客户管理员粘贴到「项目商店 → 接入设置」后加密保存。
 */
@Service
public class StoreSettingsService {

    private static final String KEY_BASE_URL = "hub.baseUrl";
    private static final String KEY_API_KEY = "hub.apiKey";

    private final SystemSettingRepository repository;
    private final SecretCrypto crypto;

    @Value("${ioedu.hub.base-url:}")
    private String envBaseUrl;

    public StoreSettingsService(SystemSettingRepository repository, SecretCrypto crypto) {
        this.repository = repository;
        this.crypto = crypto;
    }

    public String baseUrl() {
        String db = value(KEY_BASE_URL);
        String v = db == null || db.trim().isEmpty() ? envBaseUrl : db;
        return v == null ? "" : v.trim().replaceAll("/+$", "");
    }

    public String apiKey() {
        return crypto.decrypt(value(KEY_API_KEY));
    }

    public boolean isConfigured() {
        String key = apiKey();
        return !baseUrl().isEmpty() && key != null && !key.trim().isEmpty();
    }

    public Map<String, Object> view() {
        Map<String, Object> m = new LinkedHashMap<>();
        String dbUrl = value(KEY_BASE_URL);
        m.put("baseUrl", baseUrl());
        m.put("baseUrlSource", dbUrl == null || dbUrl.trim().isEmpty() ? (envBaseUrl == null || envBaseUrl.isEmpty() ? "NONE" : "ENV") : "DB");
        String key = apiKey();
        m.put("apiKeySet", key != null && !key.trim().isEmpty());
        m.put("apiKeyMasked", mask(key));
        m.put("configured", isConfigured());
        m.put("cryptoReady", crypto.isConfigured());
        return m;
    }

    @Transactional
    public Map<String, Object> update(Map<String, Object> body) {
        if (body == null || body.isEmpty()) {
            throw new BusinessException("没有需要保存的配置");
        }
        if (body.get("baseUrl") != null) {
            String v = String.valueOf(body.get("baseUrl")).trim();
            if (!v.isEmpty() && !v.startsWith("http://") && !v.startsWith("https://")) {
                throw new BusinessException("商店地址必须以 http:// 或 https:// 开头");
            }
            put(KEY_BASE_URL, v.replaceAll("/+$", ""));
        }
        if (body.get("apiKey") != null) {
            String v = String.valueOf(body.get("apiKey")).trim();
            if (!v.isEmpty()) {
                if (!v.startsWith("hk_")) {
                    throw new BusinessException("商店 API Key 应以 hk_ 开头,请向平台管理员索取");
                }
                put(KEY_API_KEY, crypto.encrypt(v));
            }
        }
        return view();
    }

    private String value(String key) {
        return repository.findById(key).map(SystemSetting::getSettingValue).orElse(null);
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
        if (key == null || key.trim().isEmpty()) {
            return "";
        }
        String k = key.trim();
        return k.length() <= 10 ? "****" : k.substring(0, 6) + "****" + k.substring(k.length() - 4);
    }
}
