package com.example.ioedunew.service;

import com.example.ioedunew.common.BusinessException;
import com.example.ioedunew.entity.SystemSetting;
import com.example.ioedunew.repository.SystemSettingRepository;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 站点设置服务:标题/LOGO/底部信息/注册开关/列表每页数量/项目与设备分类。
 * 管理后台可改,保存即生效;公开接口输出给登录页与前端布局使用。
 */
@Service
public class SiteConfigService {

    private static final String KEY_TITLE = "site.title";
    private static final String KEY_LOGO = "site.logoUrl";
    private static final String KEY_FOOTER = "site.footerText";
    private static final String KEY_ALLOW_REGISTER = "site.allowRegister";
    private static final String KEY_PROJECT_PAGE_SIZE = "site.projectPageSize";
    private static final String KEY_EQUIPMENT_PAGE_SIZE = "site.equipmentPageSize";
    private static final String KEY_PROJECT_CATEGORIES = "site.projectCategories";
    private static final String KEY_EQUIPMENT_CATEGORIES = "site.equipmentCategories";

    private static final List<String> DEFAULT_PROJECT_CATEGORIES = java.util.Arrays.asList(
            "开发板/评估板", "物联网应用", "电源管理", "消费电子", "测试测量", "通信网络", "AI应用");
    private static final List<String> DEFAULT_EQUIPMENT_CATEGORIES = java.util.Arrays.asList(
            "开发板", "测试仪表", "通信模块", "传感器", "工具");

    private final SystemSettingRepository repository;
    private final ObjectMapper objectMapper;

    public SiteConfigService(SystemSettingRepository repository, ObjectMapper objectMapper) {
        this.repository = repository;
        this.objectMapper = objectMapper;
    }

    /** 公开配置(登录页/布局/列表页使用) */
    public Map<String, Object> publicConfig() {
        Map<String, String> db = loadAll();
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("title", orDefault(db.get(KEY_TITLE), "AI未来实践中心"));
        m.put("logoUrl", orDefault(db.get(KEY_LOGO), ""));
        m.put("footerText", orDefault(db.get(KEY_FOOTER), ""));
        m.put("allowRegister", !"false".equalsIgnoreCase(db.get(KEY_ALLOW_REGISTER)));
        m.put("projectPageSize", parseInt(db.get(KEY_PROJECT_PAGE_SIZE), 9));
        m.put("equipmentPageSize", parseInt(db.get(KEY_EQUIPMENT_PAGE_SIZE), 9));
        m.put("projectCategories", parseList(db.get(KEY_PROJECT_CATEGORIES), DEFAULT_PROJECT_CATEGORIES));
        m.put("equipmentCategories", parseList(db.get(KEY_EQUIPMENT_CATEGORIES), DEFAULT_EQUIPMENT_CATEGORIES));
        return m;
    }

    public boolean registerAllowed() {
        return !"false".equalsIgnoreCase(loadAll().get(KEY_ALLOW_REGISTER));
    }

    /** 管理端保存:仅更新传入字段 */
    @Transactional
    public Map<String, Object> update(Map<String, Object> body) {
        if (body == null || body.isEmpty()) {
            throw new BusinessException("没有需要保存的配置");
        }
        if (body.get("title") != null) {
            String v = String.valueOf(body.get("title")).trim();
            if (v.isEmpty() || v.length() > 40) {
                throw new BusinessException("站点标题需为 1~40 字");
            }
            put(KEY_TITLE, v);
        }
        if (body.get("logoUrl") != null) {
            put(KEY_LOGO, String.valueOf(body.get("logoUrl")).trim());
        }
        if (body.get("footerText") != null) {
            String v = String.valueOf(body.get("footerText")).trim();
            if (v.length() > 200) {
                throw new BusinessException("底部信息不能超过 200 字");
            }
            put(KEY_FOOTER, v);
        }
        if (body.containsKey("allowRegister")) {
            put(KEY_ALLOW_REGISTER, String.valueOf(Boolean.TRUE.equals(body.get("allowRegister"))));
        }
        if (body.get("projectPageSize") != null) {
            put(KEY_PROJECT_PAGE_SIZE, String.valueOf(validPageSize(body.get("projectPageSize"))));
        }
        if (body.get("equipmentPageSize") != null) {
            put(KEY_EQUIPMENT_PAGE_SIZE, String.valueOf(validPageSize(body.get("equipmentPageSize"))));
        }
        if (body.get("projectCategories") != null) {
            put(KEY_PROJECT_CATEGORIES, toCategoryJson(body.get("projectCategories")));
        }
        if (body.get("equipmentCategories") != null) {
            put(KEY_EQUIPMENT_CATEGORIES, toCategoryJson(body.get("equipmentCategories")));
        }
        return publicConfig();
    }

    // ---------- 内部工具 ----------

    private int validPageSize(Object v) {
        int size;
        try {
            size = (int) Double.parseDouble(String.valueOf(v));
        } catch (NumberFormatException e) {
            throw new BusinessException("每页数量格式不正确");
        }
        if (size < 3 || size > 50) {
            throw new BusinessException("每页数量需在 3~50 之间");
        }
        return size;
    }

    @SuppressWarnings("unchecked")
    private String toCategoryJson(Object v) {
        if (!(v instanceof List)) {
            throw new BusinessException("分类必须是数组");
        }
        List<String> list = new ArrayList<>();
        for (Object item : (List<Object>) v) {
            String name = String.valueOf(item).trim();
            if (name.isEmpty()) {
                continue;
            }
            if (name.length() > 20) {
                throw new BusinessException("分类名不能超过 20 字:" + name);
            }
            if (!list.contains(name)) {
                list.add(name);
            }
        }
        if (list.isEmpty() || list.size() > 30) {
            throw new BusinessException("分类数量需在 1~30 之间");
        }
        try {
            return objectMapper.writeValueAsString(list);
        } catch (Exception e) {
            throw new BusinessException("分类保存失败");
        }
    }

    private List<String> parseList(String json, List<String> def) {
        if (json == null || json.trim().isEmpty()) {
            return def;
        }
        try {
            JsonNode node = objectMapper.readTree(json);
            List<String> list = new ArrayList<>();
            if (node.isArray()) {
                for (JsonNode n : node) {
                    list.add(n.asText());
                }
            }
            return list.isEmpty() ? def : list;
        } catch (Exception e) {
            return def;
        }
    }

    private Map<String, String> loadAll() {
        Map<String, String> map = new HashMap<>();
        for (SystemSetting s : repository.findAll()) {
            if (s.getSettingKey() != null && s.getSettingKey().startsWith("site.")) {
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

    private String orDefault(String v, String def) {
        return v == null || v.trim().isEmpty() ? def : v.trim();
    }

    private int parseInt(String v, int def) {
        try {
            return v == null || v.trim().isEmpty() ? def : Integer.parseInt(v.trim());
        } catch (NumberFormatException e) {
            return def;
        }
    }
}
