package com.example.ioedunew.ai.skill;

import com.example.ioedunew.common.BusinessException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * SKILL 定义(spec JSON)的结构化视图与校验:
 * {
 *   "systemPrompt": "...",                    // 必填,角色与规则
 *   "userPromptTemplate": "...{{input}}...",  // 可选,{{字段}} 会用输入替换;省略时直接把用户输入作为消息
 *   "inputSchema": {JSON Schema object},      // 可选,有则表示结构化输入(表单),也是被 Agent 当工具调用时的参数定义
 *   "outputMode": "text" | "json",
 *   "tools": ["equipment.search", ...],       // 允许模型调用的工具白名单
 *   "model": { "temperature": 0.4, "maxTokens": 1500 },
 *   "greeting": "开场白", "examples": ["示例问题", ...]
 * }
 */
public class SkillSpec {

    private static final Pattern PLACEHOLDER = Pattern.compile("\\{\\{\\s*([A-Za-z0-9_]+)\\s*}}");

    private final ObjectNode root;

    private SkillSpec(ObjectNode root) {
        this.root = root;
    }

    public static SkillSpec parse(ObjectMapper om, String json) {
        try {
            JsonNode n = om.readTree(json == null || json.trim().isEmpty() ? "{}" : json);
            if (!n.isObject()) {
                throw new BusinessException("SKILL 定义必须是 JSON 对象");
            }
            return new SkillSpec((ObjectNode) n);
        } catch (BusinessException e) {
            throw e;
        } catch (Exception e) {
            throw new BusinessException("SKILL 定义不是合法 JSON");
        }
    }

    /** 保存前校验并规整;toolNames 为注册中心里全部工具名 */
    public void validate(List<String> toolNames) {
        String sp = systemPrompt();
        if (sp.isEmpty()) {
            throw new BusinessException("系统提示词(systemPrompt)不能为空");
        }
        if (sp.length() > 8000) {
            throw new BusinessException("系统提示词不能超过 8000 字");
        }
        String mode = outputMode();
        if (!"text".equals(mode) && !"json".equals(mode)) {
            throw new BusinessException("outputMode 只能是 text 或 json");
        }
        for (String t : tools()) {
            if (!toolNames.contains(t)) {
                throw new BusinessException("未知工具: " + t);
            }
        }
        double temp = temperature(0.4);
        if (temp < 0 || temp > 2) {
            throw new BusinessException("temperature 需在 0~2 之间");
        }
        int max = maxTokens(1500);
        if (max < 200 || max > 8000) {
            throw new BusinessException("maxTokens 需在 200~8000 之间");
        }
        JsonNode schema = root.get("inputSchema");
        if (schema != null && !schema.isNull() && (!schema.isObject() || !"object".equals(schema.path("type").asText("object")))) {
            throw new BusinessException("inputSchema 必须是 type=object 的 JSON Schema");
        }
    }

    public ObjectNode raw() {
        return root;
    }

    public String systemPrompt() {
        return root.path("systemPrompt").asText("").trim();
    }

    public String userPromptTemplate() {
        return root.path("userPromptTemplate").asText("");
    }

    public String outputMode() {
        String m = root.path("outputMode").asText("text").trim().toLowerCase();
        return m.isEmpty() ? "text" : m;
    }

    public boolean jsonMode() {
        return "json".equals(outputMode());
    }

    public List<String> tools() {
        List<String> list = new ArrayList<>();
        for (JsonNode n : root.path("tools")) {
            String v = n.asText("").trim();
            if (!v.isEmpty() && !list.contains(v)) {
                list.add(v);
            }
        }
        return list;
    }

    public double temperature(double def) {
        return root.path("model").path("temperature").asDouble(def);
    }

    public int maxTokens(int def) {
        return root.path("model").path("maxTokens").asInt(def);
    }

    public JsonNode inputSchema() {
        JsonNode s = root.get("inputSchema");
        return s == null || s.isNull() || !s.isObject() ? null : s;
    }

    public boolean hasStructuredInput() {
        JsonNode s = inputSchema();
        return s != null && s.path("properties").size() > 0;
    }

    /**
     * 把用户输入渲染成消息文本:有模板则替换 {{input}} / {{字段}};
     * 无模板时,结构化输入按 "字段: 值" 逐行拼接,纯文本直接返回。
     */
    public String renderUserMessage(JsonNode input) {
        String template = userPromptTemplate();
        if (template.trim().isEmpty()) {
            if (input == null || input.isNull()) {
                return "";
            }
            if (input.isTextual()) {
                return input.asText();
            }
            StringBuilder sb = new StringBuilder();
            input.fields().forEachRemaining(e -> sb.append(e.getKey()).append(": ")
                    .append(e.getValue().isTextual() ? e.getValue().asText() : e.getValue().toString()).append('\n'));
            return sb.toString().trim();
        }
        Matcher m = PLACEHOLDER.matcher(template);
        StringBuffer out = new StringBuffer();
        while (m.find()) {
            String key = m.group(1);
            String value;
            if ("input".equals(key)) {
                value = input == null ? "" : input.isTextual() ? input.asText() : input.toString();
            } else {
                JsonNode v = input == null ? null : input.get(key);
                value = v == null || v.isNull() ? "" : v.isTextual() ? v.asText() : v.toString();
            }
            m.appendReplacement(out, Matcher.quoteReplacement(value));
        }
        m.appendTail(out);
        return out.toString();
    }
}
