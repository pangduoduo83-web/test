package com.example.ioedunew.ai.skill;

import com.example.ioedunew.ai.tool.AiTool;
import com.example.ioedunew.ai.tool.ToolRegistry;
import com.example.ioedunew.common.BusinessException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * 工具策略:注册中心里的工具默认全部启用、按工具自身声明的角色与只读性决定是否需确认;
 * 管理员可按租户覆盖(关闭、提高最低角色、强制确认、每人每日次数)。
 */
@Service
public class ToolPolicyService {

    private final ToolRegistry registry;
    private final AiToolPolicyRepository policyRepo;
    private final AiToolInvocationRepository invocationRepo;

    public ToolPolicyService(ToolRegistry registry, AiToolPolicyRepository policyRepo,
                             AiToolInvocationRepository invocationRepo) {
        this.registry = registry;
        this.policyRepo = policyRepo;
        this.invocationRepo = invocationRepo;
    }

    /** 该角色当前可用的工具(已启用且角色满足) */
    public List<AiTool> availableFor(String role) {
        List<AiTool> list = new ArrayList<>();
        for (AiTool t : registry.all()) {
            Effective e = effective(t);
            if (e.enabled && roleAllows(e.minRole, role)) {
                list.add(t);
            }
        }
        return list;
    }

    public Effective effective(AiTool tool) {
        Optional<AiToolPolicy> p = policyRepo.findByToolName(tool.name());
        Effective e = new Effective();
        e.toolName = tool.name();
        e.enabled = p.map(AiToolPolicy::getEnabled).orElse(true);
        e.minRole = p.map(AiToolPolicy::getMinRole).filter(r -> r != null && !r.isEmpty()).orElse(tool.requiredRole());
        e.requiresConfirmation = !tool.readOnly() || p.map(AiToolPolicy::getRequiresConfirmation).orElse(false);
        e.dailyLimit = p.map(AiToolPolicy::getDailyLimit).orElse(null);
        return e;
    }

    /** 检查某用户今天对该工具的调用是否已达上限 */
    public void checkDailyLimit(Effective e, Long userId) {
        if (e.dailyLimit == null || e.dailyLimit <= 0) {
            return;
        }
        long used = invocationRepo.countByUserIdAndToolNameAndCreatedAtAfter(userId, e.toolName,
                LocalDate.now().atStartOfDay());
        if (used >= e.dailyLimit) {
            throw new BusinessException("工具 " + e.toolName + " 今日调用次数已达上限(" + e.dailyLimit + ")");
        }
    }

    public List<Map<String, Object>> listForAdmin() {
        List<Map<String, Object>> list = new ArrayList<>();
        for (AiTool t : registry.all()) {
            Effective e = effective(t);
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("name", t.name());
            m.put("description", t.description());
            m.put("readOnly", t.readOnly());
            m.put("defaultRole", t.requiredRole());
            m.put("inputSchema", t.inputSchema());
            m.put("enabled", e.enabled);
            m.put("minRole", e.minRole);
            m.put("requiresConfirmation", e.requiresConfirmation);
            m.put("dailyLimit", e.dailyLimit);
            list.add(m);
        }
        return list;
    }

    /** body: { enabled?, minRole?, requiresConfirmation?, dailyLimit? } */
    @Transactional
    public Map<String, Object> update(String toolName, Map<String, Object> body) {
        AiTool tool = registry.find(toolName).orElseThrow(() -> new BusinessException(404, "工具不存在: " + toolName));
        AiToolPolicy p = policyRepo.findByToolName(toolName).orElseGet(() -> {
            AiToolPolicy n = new AiToolPolicy();
            n.setToolName(toolName);
            n.setRequiresConfirmation(!tool.readOnly());
            return n;
        });
        if (body.containsKey("enabled")) {
            p.setEnabled(Boolean.TRUE.equals(body.get("enabled")));
        }
        if (body.containsKey("minRole")) {
            Object r = body.get("minRole");
            String role = r == null ? null : String.valueOf(r).trim().toUpperCase();
            if (role != null && !role.isEmpty() && !"TEACHER".equals(role) && !"ADMIN".equals(role) && !"STUDENT".equals(role)) {
                throw new BusinessException("minRole 只能是 STUDENT / TEACHER / ADMIN 或留空");
            }
            p.setMinRole(role == null || role.isEmpty() || "STUDENT".equals(role) ? null : role);
        }
        if (body.containsKey("requiresConfirmation")) {
            boolean v = Boolean.TRUE.equals(body.get("requiresConfirmation"));
            p.setRequiresConfirmation(!tool.readOnly() || v);
        }
        if (body.containsKey("dailyLimit")) {
            Object v = body.get("dailyLimit");
            p.setDailyLimit(v == null || String.valueOf(v).isEmpty() ? null : (int) Double.parseDouble(String.valueOf(v)));
        }
        p.setUpdatedAt(LocalDateTime.now());
        policyRepo.save(p);
        for (Map<String, Object> m : listForAdmin()) {
            if (toolName.equals(m.get("name"))) {
                return m;
            }
        }
        return new LinkedHashMap<>();
    }

    public static boolean roleAllows(String minRole, String role) {
        if (minRole == null || minRole.isEmpty()) {
            return true;
        }
        if ("ADMIN".equals(minRole)) {
            return "ADMIN".equals(role);
        }
        if ("TEACHER".equals(minRole)) {
            return "ADMIN".equals(role) || "TEACHER".equals(role);
        }
        return true;
    }

    public static class Effective {
        public String toolName;
        public boolean enabled;
        public String minRole;
        public boolean requiresConfirmation;
        public Integer dailyLimit;
    }
}
