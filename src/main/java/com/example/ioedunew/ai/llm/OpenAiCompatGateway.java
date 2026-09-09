package com.example.ioedunew.ai.llm;

import com.example.ioedunew.service.AiConfigService;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.springframework.stereotype.Component;

import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Scanner;

/**
 * OpenAI 兼容协议(/chat/completions)实现,支持 tools/tool_choice、response_format 与 stream:true。
 * 流式用 HttpURLConnection 逐行读 SSE(data: {...}),按 index 累积 tool_calls 的分片参数;
 * 读超时按「两个分片之间的空闲时间」计,不对整体时长设限。
 */
@Component
public class OpenAiCompatGateway implements LlmGateway {

    private final ObjectMapper objectMapper;

    public OpenAiCompatGateway(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    @Override
    public ChatResult chat(AiConfigService.AiConfig cfg, ChatRequest request) throws Exception {
        HttpURLConnection conn = open(cfg, request, false);
        int status = conn.getResponseCode();
        String body = readAll(status >= 400 ? conn.getErrorStream() : conn.getInputStream());
        if (status >= 400) {
            throw new IllegalStateException("模型接口 HTTP " + status + ": " + errorMessage(body));
        }
        JsonNode root = objectMapper.readTree(body);
        JsonNode choice = root.path("choices").path(0);
        ChatResult result = new ChatResult();
        result.setContent(choice.path("message").path("content").asText(""));
        result.setFinishReason(choice.path("finish_reason").asText(null));
        Map<String, String> names = wireNames(request);
        for (JsonNode tc : choice.path("message").path("tool_calls")) {
            result.getToolCalls().add(new ToolCall(tc.path("id").asText(),
                    internalName(names, tc.path("function").path("name").asText()),
                    tc.path("function").path("arguments").asText("{}")));
        }
        result.setPromptTokens(root.path("usage").path("prompt_tokens").asInt(0));
        result.setCompletionTokens(root.path("usage").path("completion_tokens").asInt(0));
        if (result.getContent().isEmpty() && !result.hasToolCalls()) {
            throw new IllegalStateException("模型响应为空");
        }
        return result;
    }

    @Override
    public void stream(AiConfigService.AiConfig cfg, ChatRequest request, StreamListener listener) throws Exception {
        HttpURLConnection conn = open(cfg, request, true);
        int status = conn.getResponseCode();
        if (status >= 400) {
            throw new IllegalStateException("模型接口 HTTP " + status + ": " + errorMessage(readAll(conn.getErrorStream())));
        }
        ChatResult result = new ChatResult();
        StringBuilder content = new StringBuilder();
        Map<Integer, ToolCall> calls = new LinkedHashMap<>();
        Map<Integer, StringBuilder> args = new LinkedHashMap<>();
        Map<String, String> names = wireNames(request);
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(conn.getInputStream(), StandardCharsets.UTF_8))) {
            String line;
            while ((line = reader.readLine()) != null) {
                if (!line.startsWith("data:")) {
                    continue;
                }
                String data = line.substring(5).trim();
                if (data.isEmpty()) {
                    continue;
                }
                if ("[DONE]".equals(data)) {
                    break;
                }
                JsonNode chunk = objectMapper.readTree(data);
                JsonNode usage = chunk.path("usage");
                if (!usage.isMissingNode() && !usage.isNull()) {
                    result.setPromptTokens(usage.path("prompt_tokens").asInt(result.getPromptTokens()));
                    result.setCompletionTokens(usage.path("completion_tokens").asInt(result.getCompletionTokens()));
                }
                JsonNode choice = chunk.path("choices").path(0);
                if (choice.isMissingNode()) {
                    continue;
                }
                JsonNode delta = choice.path("delta");
                String text = delta.path("content").asText(null);
                if (text != null && !text.isEmpty()) {
                    content.append(text);
                    listener.onContent(text);
                }
                for (JsonNode tc : delta.path("tool_calls")) {
                    int index = tc.path("index").asInt(calls.size());
                    ToolCall call = calls.computeIfAbsent(index, k -> new ToolCall());
                    if (tc.hasNonNull("id")) {
                        call.setId(tc.get("id").asText());
                    }
                    JsonNode fn = tc.path("function");
                    if (fn.hasNonNull("name")) {
                        call.setName(internalName(names, fn.get("name").asText()));
                    }
                    if (fn.hasNonNull("arguments")) {
                        args.computeIfAbsent(index, k -> new StringBuilder()).append(fn.get("arguments").asText());
                    }
                }
                String finish = choice.path("finish_reason").asText(null);
                if (finish != null && !finish.isEmpty()) {
                    result.setFinishReason(finish);
                }
            }
        }
        result.setContent(content.toString());
        for (Map.Entry<Integer, ToolCall> e : calls.entrySet()) {
            ToolCall call = e.getValue();
            StringBuilder sb = args.get(e.getKey());
            call.setArguments(sb == null || sb.length() == 0 ? "{}" : sb.toString());
            if (call.getId() == null) {
                call.setId("call_" + e.getKey());
            }
            result.getToolCalls().add(call);
        }
        listener.onComplete(result);
    }

    private HttpURLConnection open(AiConfigService.AiConfig cfg, ChatRequest request, boolean stream) throws Exception {
        ObjectNode body = objectMapper.createObjectNode();
        body.put("model", cfg.model);
        body.put("temperature", request.getTemperature() == null ? cfg.temperature : request.getTemperature());
        body.put("max_tokens", request.getMaxTokens() == null ? cfg.maxTokens : Math.min(request.getMaxTokens(), cfg.maxTokens));
        body.put("stream", stream);
        if (stream) {
            body.putObject("stream_options").put("include_usage", true);
        }
        if (request.isJsonMode()) {
            body.putObject("response_format").put("type", "json_object");
        }
        ArrayNode messages = body.putArray("messages");
        for (ChatMessage m : request.getMessages()) {
            ObjectNode n = messages.addObject();
            n.put("role", m.getRole());
            if (m.getContent() != null || m.getToolCalls().isEmpty()) {
                n.put("content", m.getContent() == null ? "" : m.getContent());
            }
            if ("tool".equals(m.getRole())) {
                n.put("tool_call_id", m.getToolCallId());
                if (m.getName() != null) {
                    n.put("name", wireName(m.getName()));
                }
            }
            if (!m.getToolCalls().isEmpty()) {
                ArrayNode tcs = n.putArray("tool_calls");
                for (ToolCall tc : m.getToolCalls()) {
                    ObjectNode t = tcs.addObject();
                    t.put("id", tc.getId());
                    t.put("type", "function");
                    ObjectNode fn = t.putObject("function");
                    fn.put("name", wireName(tc.getName()));
                    fn.put("arguments", tc.getArguments() == null ? "{}" : tc.getArguments());
                }
            }
        }
        if (!request.getTools().isEmpty()) {
            ArrayNode tools = body.putArray("tools");
            for (ToolSpec spec : request.getTools()) {
                ObjectNode t = tools.addObject();
                t.put("type", "function");
                ObjectNode fn = t.putObject("function");
                fn.put("name", wireName(spec.getName()));
                fn.put("description", spec.getDescription());
                fn.set("parameters", spec.getParameters());
            }
            body.put("tool_choice", "auto");
        }

        URL url = new URL(cfg.baseUrl.replaceAll("/+$", "") + "/chat/completions");
        HttpURLConnection conn = (HttpURLConnection) url.openConnection();
        conn.setRequestMethod("POST");
        conn.setConnectTimeout(cfg.connectTimeoutMs);
        // 流式:readTimeout 只约束相邻分片的空闲间隔;非流式则是等待整个回复的时长
        conn.setReadTimeout(stream ? Math.max(cfg.readTimeoutMs, 60_000) : cfg.readTimeoutMs);
        conn.setRequestProperty("Content-Type", "application/json; charset=UTF-8");
        conn.setRequestProperty("Authorization", "Bearer " + cfg.apiKey.trim());
        conn.setRequestProperty("Accept", stream ? "text/event-stream" : "application/json");
        conn.setDoOutput(true);
        byte[] payload = objectMapper.writeValueAsBytes(body);
        try (OutputStream out = conn.getOutputStream()) {
            out.write(payload);
        }
        return conn;
    }

    /**
     * 平台内部工具名用点分隔(如 project.get),而 OpenAI 兼容接口(DeepSeek 等)只接受 ^[a-zA-Z0-9_-]+$,
     * 发送前把非法字符换成下划线,收到模型的调用后再按本次请求的工具表映射回内部名。
     */
    static String wireName(String name) {
        return name == null ? null : name.replaceAll("[^A-Za-z0-9_-]", "_");
    }

    private static Map<String, String> wireNames(ChatRequest request) {
        Map<String, String> map = new LinkedHashMap<>();
        for (ToolSpec spec : request.getTools()) {
            map.put(wireName(spec.getName()), spec.getName());
        }
        for (ChatMessage m : request.getMessages()) {
            for (ToolCall tc : m.getToolCalls()) {
                if (tc.getName() != null) {
                    map.putIfAbsent(wireName(tc.getName()), tc.getName());
                }
            }
        }
        return map;
    }

    private static String internalName(Map<String, String> names, String wire) {
        return names.getOrDefault(wire, wire);
    }

    private String readAll(InputStream in) {
        if (in == null) {
            return "";
        }
        try (Scanner scanner = new Scanner(in, StandardCharsets.UTF_8.name()).useDelimiter("\\A")) {
            return scanner.hasNext() ? scanner.next() : "";
        }
    }

    private String errorMessage(String body) {
        try {
            JsonNode root = objectMapper.readTree(body);
            String msg = root.path("error").path("message").asText(null);
            return msg == null ? (body.length() > 200 ? body.substring(0, 200) : body) : msg;
        } catch (Exception e) {
            return body.length() > 200 ? body.substring(0, 200) : body;
        }
    }

    /** 供上层把 OpenAI 风格的 tool_calls 转回消息历史时使用 */
    public static List<ToolCall> copy(List<ToolCall> calls) {
        List<ToolCall> list = new ArrayList<>();
        for (ToolCall c : calls) {
            list.add(new ToolCall(c.getId(), c.getName(), c.getArguments()));
        }
        return list;
    }
}
