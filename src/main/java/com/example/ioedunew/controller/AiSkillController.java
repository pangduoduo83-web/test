package com.example.ioedunew.controller;

import com.example.ioedunew.ai.skill.AiConversation;
import com.example.ioedunew.ai.skill.AiConversationRepository;
import com.example.ioedunew.ai.skill.AiMessage;
import com.example.ioedunew.ai.skill.AiMessageRepository;
import com.example.ioedunew.ai.skill.AiSkillVersion;
import com.example.ioedunew.ai.skill.SkillRunner;
import com.example.ioedunew.ai.skill.AiSkillService;
import com.example.ioedunew.ai.skill.ToolPolicyService;
import com.example.ioedunew.ai.tool.AiTool;
import com.example.ioedunew.common.ApiResponse;
import com.example.ioedunew.common.BusinessException;
import com.example.ioedunew.config.AuthUser;
import com.example.ioedunew.tenant.TenantContext;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.http.MediaType;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestAttribute;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import javax.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * AI 应用接口(需登录):SKILL 列表与自定义、对话(流式 SSE / 非流式)、会话历史、可用工具。
 * SSE 事件:delta{text} / tool_call{name,args} / tool_result{name,ok,summary} / confirm_required{tool,args,description}
 *          / done{runId,conversationId,content,...} / error{message}
 */
@RestController
@RequestMapping("/api/ai")
public class AiSkillController {

    private final AiSkillService skillService;
    private final SkillRunner runner;
    private final ToolPolicyService policyService;
    private final AiConversationRepository conversationRepo;
    private final AiMessageRepository messageRepo;
    private final ThreadPoolTaskExecutor aiExecutor;
    private final ObjectMapper om;

    public AiSkillController(AiSkillService skillService, SkillRunner runner, ToolPolicyService policyService,
                             AiConversationRepository conversationRepo, AiMessageRepository messageRepo,
                             @Qualifier("aiExecutor") ThreadPoolTaskExecutor aiExecutor, ObjectMapper om) {
        this.skillService = skillService;
        this.runner = runner;
        this.policyService = policyService;
        this.conversationRepo = conversationRepo;
        this.messageRepo = messageRepo;
        this.aiExecutor = aiExecutor;
        this.om = om;
    }

    // ---------- SKILL ----------

    @GetMapping("/skills")
    public ApiResponse<List<Map<String, Object>>> skills(@RequestAttribute(AuthUser.REQUEST_ATTR) AuthUser user) {
        return ApiResponse.ok(skillService.visibleTo(user));
    }

    @GetMapping("/skills/{key}")
    public ApiResponse<Map<String, Object>> skill(@PathVariable String key,
                                                  @RequestAttribute(AuthUser.REQUEST_ATTR) AuthUser user) {
        return ApiResponse.ok(skillService.detail(key, user));
    }

    @PostMapping("/skills")
    public ApiResponse<Map<String, Object>> createSkill(@RequestBody JsonNode body,
                                                        @RequestAttribute(AuthUser.REQUEST_ATTR) AuthUser user) {
        return ApiResponse.ok(skillService.create(body, user));
    }

    @PutMapping("/skills/{id}")
    public ApiResponse<Map<String, Object>> updateSkill(@PathVariable Long id, @RequestBody JsonNode body,
                                                        @RequestAttribute(AuthUser.REQUEST_ATTR) AuthUser user) {
        return ApiResponse.ok(skillService.update(id, body, user));
    }

    @DeleteMapping("/skills/{id}")
    public ApiResponse<Void> deleteSkill(@PathVariable Long id, @RequestAttribute(AuthUser.REQUEST_ATTR) AuthUser user) {
        skillService.delete(id, user);
        return ApiResponse.ok();
    }

    /** 另存为自己的 SKILL,body: { name?, scope? } */
    @PostMapping("/skills/{key}/duplicate")
    public ApiResponse<Map<String, Object>> duplicate(@PathVariable String key, @RequestBody(required = false) JsonNode body,
                                                      @RequestAttribute(AuthUser.REQUEST_ATTR) AuthUser user) {
        return ApiResponse.ok(skillService.duplicate(key, body == null ? om.createObjectNode() : body, user));
    }

    @GetMapping("/skills/{id}/versions")
    public ApiResponse<List<AiSkillVersion>> versions(@PathVariable Long id,
                                                      @RequestAttribute(AuthUser.REQUEST_ATTR) AuthUser user) {
        return ApiResponse.ok(skillService.versions(id, user));
    }

    /** 当前用户可用的工具(用于 SKILL 编辑器里勾选) */
    @GetMapping("/tools")
    public ApiResponse<List<Map<String, Object>>> tools(@RequestAttribute(AuthUser.REQUEST_ATTR) AuthUser user) {
        List<Map<String, Object>> list = new ArrayList<>();
        for (AiTool t : policyService.availableFor(user.getRole())) {
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("name", t.name());
            m.put("description", t.description());
            m.put("readOnly", t.readOnly());
            m.put("requiresConfirmation", policyService.effective(t).requiresConfirmation);
            list.add(m);
        }
        return ApiResponse.ok(list);
    }

    // ---------- 对话 ----------

    /** 非流式:body { skillKey?, conversationId?, input, confirm? } */
    @PostMapping("/chat")
    public ApiResponse<Map<String, Object>> chat(@RequestBody JsonNode body,
                                                 @RequestAttribute(AuthUser.REQUEST_ATTR) AuthUser user) {
        Map<String, Object> out = new LinkedHashMap<>();
        List<Map<String, Object>> toolEvents = new ArrayList<>();
        out.put("tools", toolEvents);
        final String[] error = new String[1];
        runner.run(user, toRequest(body), new SkillRunner.Events() {
            public void delta(String text) {
            }

            public void toolCall(String name, JsonNode args) {
                Map<String, Object> m = new LinkedHashMap<>();
                m.put("name", name);
                m.put("args", args);
                toolEvents.add(m);
            }

            public void toolResult(String name, boolean ok, String summary) {
                for (int i = toolEvents.size() - 1; i >= 0; i--) {
                    if (name.equals(toolEvents.get(i).get("name")) && !toolEvents.get(i).containsKey("ok")) {
                        toolEvents.get(i).put("ok", ok);
                        toolEvents.get(i).put("summary", summary);
                        break;
                    }
                }
            }

            public void confirmRequired(String toolName, JsonNode args, String description) {
                Map<String, Object> m = new LinkedHashMap<>();
                m.put("tool", toolName);
                m.put("args", args);
                m.put("description", description);
                out.put("confirmRequired", m);
            }

            public void done(Map<String, Object> result) {
                out.putAll(result);
            }

            public void error(String message) {
                error[0] = message;
            }
        }, false);
        if (error[0] != null) {
            throw new BusinessException(error[0]);
        }
        return ApiResponse.ok(out);
    }

    /** 流式 SSE:同 /chat 的请求体 */
    @PostMapping(value = "/chat/stream", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public SseEmitter chatStream(@RequestBody JsonNode body,
                                 @RequestAttribute(AuthUser.REQUEST_ATTR) AuthUser user,
                                 HttpServletResponse response) {
        response.setHeader("X-Accel-Buffering", "no");
        response.setHeader("Cache-Control", "no-cache");
        SseEmitter emitter = new SseEmitter(600_000L);
        AtomicBoolean closed = new AtomicBoolean(false);
        emitter.onCompletion(() -> closed.set(true));
        emitter.onTimeout(() -> closed.set(true));
        emitter.onError(e -> closed.set(true));
        String tenant = TenantContext.require();
        SkillRunner.Request req = toRequest(body);
        aiExecutor.execute(() -> TenantContext.runAs(tenant, () -> runner.run(user, req, new SkillRunner.Events() {
            public void delta(String text) {
                send(emitter, closed, "delta", om.createObjectNode().put("text", text));
            }

            public void toolCall(String name, JsonNode args) {
                ObjectNode n = om.createObjectNode().put("name", name);
                n.set("args", args);
                send(emitter, closed, "tool_call", n);
            }

            public void toolResult(String name, boolean ok, String summary) {
                send(emitter, closed, "tool_result", om.createObjectNode().put("name", name).put("ok", ok).put("summary", summary));
            }

            public void confirmRequired(String toolName, JsonNode args, String description) {
                ObjectNode n = om.createObjectNode().put("tool", toolName).put("description", description);
                n.set("args", args);
                send(emitter, closed, "confirm_required", n);
            }

            public void done(Map<String, Object> result) {
                send(emitter, closed, "done", om.valueToTree(result));
                if (!closed.get()) {
                    emitter.complete();
                }
            }

            public void error(String message) {
                send(emitter, closed, "error", om.createObjectNode().put("message", message));
                if (!closed.get()) {
                    emitter.complete();
                }
            }
        }, true)));
        return emitter;
    }

    @GetMapping("/conversations")
    public ApiResponse<List<AiConversation>> conversations(@RequestAttribute(AuthUser.REQUEST_ATTR) AuthUser user) {
        return ApiResponse.ok(conversationRepo.findTop50ByUserIdOrderByUpdatedAtDesc(user.getId()));
    }

    @GetMapping("/conversations/{id}/messages")
    public ApiResponse<List<ObjectNode>> messages(@PathVariable Long id,
                                                  @RequestAttribute(AuthUser.REQUEST_ATTR) AuthUser user) {
        AiConversation c = conversationRepo.findById(id).orElseThrow(() -> new BusinessException(404, "会话不存在"));
        if (!c.getUserId().equals(user.getId())) {
            throw new BusinessException(403, "无权访问他人会话");
        }
        List<ObjectNode> list = new ArrayList<>();
        for (AiMessage m : messageRepo.findByConversationIdOrderByIdAsc(id)) {
            if ("tool".equals(m.getRole())) {
                continue;
            }
            list.add(runner.messageView(m));
        }
        return ApiResponse.ok(list);
    }

    @DeleteMapping("/conversations/{id}")
    public ApiResponse<Void> deleteConversation(@PathVariable Long id,
                                                @RequestAttribute(AuthUser.REQUEST_ATTR) AuthUser user) {
        AiConversation c = conversationRepo.findById(id).orElseThrow(() -> new BusinessException(404, "会话不存在"));
        if (!c.getUserId().equals(user.getId())) {
            throw new BusinessException(403, "无权删除他人会话");
        }
        messageRepo.deleteByConversationId(id);
        conversationRepo.delete(c);
        return ApiResponse.ok();
    }

    private SkillRunner.Request toRequest(JsonNode body) {
        SkillRunner.Request req = new SkillRunner.Request();
        req.skillKey = body.path("skillKey").asText(null);
        req.conversationId = body.hasNonNull("conversationId") ? body.get("conversationId").asLong() : null;
        req.input = body.get("input");
        req.confirm = body.path("confirm").asBoolean(false);
        req.context = body.get("context");
        req.title = body.path("title").asText(null);
        return req;
    }

    private void send(SseEmitter emitter, AtomicBoolean closed, String event, JsonNode data) {
        if (closed.get()) {
            return;
        }
        try {
            emitter.send(SseEmitter.event().name(event).data(om.writeValueAsString(data), MediaType.APPLICATION_JSON));
        } catch (IOException | IllegalStateException e) {
            closed.set(true);
        }
    }
}
