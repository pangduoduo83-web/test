package com.example.ioedunew.ai.skill;

import com.example.ioedunew.ai.tool.AiTool;
import com.example.ioedunew.ai.tool.ToolRegistry;
import com.example.ioedunew.common.BusinessException;
import com.example.ioedunew.config.AuthUser;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.io.Resource;
import org.springframework.core.io.support.PathMatchingResourcePatternResolver;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import javax.annotation.PostConstruct;
import java.io.InputStream;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.regex.Pattern;

/**
 * SKILL 管理:内置(classpath:skills/*.json,只读,随版本发布)、本站共享(TENANT)、个人(PERSONAL)三层,
 * 统一用同一份 spec 模型;支持「另存为」把内置/他人的 SKILL 复制成自己的再改。
 */
@Service
public class AiSkillService {

    private static final Logger log = LoggerFactory.getLogger(AiSkillService.class);
    private static final Pattern KEY = Pattern.compile("^[a-z][a-z0-9-]{1,59}$");

    private final AiSkillRepository skillRepo;
    private final AiSkillVersionRepository versionRepo;
    private final ToolRegistry toolRegistry;
    private final ObjectMapper om;

    /** 内置 SKILL:key → 实体(未持久化,id 为空) */
    private final Map<String, AiSkill> builtins = new LinkedHashMap<>();

    public AiSkillService(AiSkillRepository skillRepo, AiSkillVersionRepository versionRepo,
                        ToolRegistry toolRegistry, ObjectMapper om) {
        this.skillRepo = skillRepo;
        this.versionRepo = versionRepo;
        this.toolRegistry = toolRegistry;
        this.om = om;
    }

    @PostConstruct
    void loadBuiltins() throws Exception {
        Resource[] resources = new PathMatchingResourcePatternResolver().getResources("classpath:skills/*.json");
        for (Resource r : resources) {
            try (InputStream in = r.getInputStream()) {
                JsonNode n = om.readTree(in);
                AiSkill s = new AiSkill();
                s.setSkillKey(n.path("key").asText());
                s.setName(n.path("name").asText());
                s.setDescription(n.path("description").asText(null));
                s.setIcon(n.path("icon").asText("✨"));
                s.setCategory(n.path("category").asText(null));
                s.setScope(AiSkill.SCOPE_BUILTIN);
                s.setSpec(n.path("spec").toString());
                s.setVersion(1);
                SkillSpec.parse(om, s.getSpec()).validate(toolNames());
                builtins.put(s.getSkillKey(), s);
            } catch (Exception e) {
                log.error("内置 SKILL {} 加载失败: {}", r.getFilename(), e.getMessage());
            }
        }
        log.info("已加载 {} 个内置 SKILL: {}", builtins.size(), builtins.keySet());
    }

    public List<String> toolNames() {
        List<String> names = new ArrayList<>();
        for (AiTool t : toolRegistry.all()) {
            names.add(t.name());
        }
        return names;
    }

    /** 用户可见的 SKILL:内置 + 本站共享(启用的)+ 自己的个人 SKILL */
    public List<Map<String, Object>> visibleTo(AuthUser user) {
        List<Map<String, Object>> list = new ArrayList<>();
        for (AiSkill s : builtins.values()) {
            list.add(view(s, user));
        }
        for (AiSkill s : skillRepo.findByScopeOrderByUpdatedAtDesc(AiSkill.SCOPE_TENANT)) {
            if ("ACTIVE".equals(s.getStatus())) {
                list.add(view(s, user));
            }
        }
        for (AiSkill s : skillRepo.findByScopeAndOwnerUserIdOrderByUpdatedAtDesc(AiSkill.SCOPE_PERSONAL, user.getId())) {
            list.add(view(s, user));
        }
        return list;
    }

    /** 管理端:本站全部入库 SKILL(含所有人的个人 SKILL) */
    public List<Map<String, Object>> allStored(AuthUser admin) {
        List<Map<String, Object>> list = new ArrayList<>();
        for (AiSkill s : skillRepo.findAllByOrderByUpdatedAtDesc()) {
            list.add(view(s, admin));
        }
        return list;
    }

    /** 解析并校验用户能否使用该 SKILL(运行时用) */
    public AiSkill resolveUsable(String key, AuthUser user) {
        AiSkill s = builtins.get(key);
        if (s == null) {
            s = skillRepo.findBySkillKey(key).orElseThrow(() -> new BusinessException(404, "SKILL 不存在: " + key));
        }
        if (!AiSkill.SCOPE_BUILTIN.equals(s.getScope()) && !"ACTIVE".equals(s.getStatus()) && !canManage(s, user)) {
            throw new BusinessException(403, "该 SKILL 已停用");
        }
        if (AiSkill.SCOPE_PERSONAL.equals(s.getScope()) && !user.getId().equals(s.getOwnerUserId()) && !user.isAdmin()) {
            throw new BusinessException(403, "无权使用他人的个人 SKILL");
        }
        return s;
    }

    public Map<String, Object> detail(String key, AuthUser user) {
        AiSkill s = resolveUsable(key, user);
        Map<String, Object> m = view(s, user);
        try {
            m.put("spec", om.readTree(s.getSpec()));
        } catch (Exception e) {
            m.put("spec", null);
        }
        return m;
    }

    /**
     * 新建:scope=TENANT 需教师/管理员;PERSONAL 任何登录用户。
     * body: { key?, name, description, icon, category, scope, spec{} }
     */
    @Transactional
    public Map<String, Object> create(JsonNode body, AuthUser user) {
        String scope = body.path("scope").asText(AiSkill.SCOPE_PERSONAL).toUpperCase(Locale.ROOT);
        if (AiSkill.SCOPE_TENANT.equals(scope) && !user.isAdmin() && !user.isTeacher()) {
            throw new BusinessException(403, "只有教师或管理员可以创建本站共享 SKILL");
        }
        if (!AiSkill.SCOPE_TENANT.equals(scope) && !AiSkill.SCOPE_PERSONAL.equals(scope)) {
            throw new BusinessException("scope 只能是 TENANT 或 PERSONAL");
        }
        String key = body.path("key").asText("").trim().toLowerCase(Locale.ROOT);
        if (key.isEmpty()) {
            key = "s-" + Long.toString(System.currentTimeMillis(), 36) + "-" + user.getId();
        }
        if (!KEY.matcher(key).matches()) {
            throw new BusinessException("SKILL 标识须为小写字母开头的 2~60 位字母/数字/短横线");
        }
        if (builtins.containsKey(key) || skillRepo.findBySkillKey(key).isPresent()) {
            throw new BusinessException(409, "SKILL 标识已存在: " + key);
        }
        AiSkill s = new AiSkill();
        s.setSkillKey(key);
        s.setScope(scope);
        s.setOwnerUserId(AiSkill.SCOPE_PERSONAL.equals(scope) ? user.getId() : null);
        s.setCreatedBy(user.getId());
        s.setForkedFrom(body.path("forkedFrom").asText(null));
        applyMeta(s, body);
        applySpec(s, body.get("spec"));
        s = skillRepo.save(s);
        snapshot(s, "创建", user.getId());
        return view(s, user);
    }

    @Transactional
    public Map<String, Object> update(Long id, JsonNode body, AuthUser user) {
        AiSkill s = stored(id);
        if (!canManage(s, user)) {
            throw new BusinessException(403, "无权修改该 SKILL");
        }
        applyMeta(s, body);
        if (body.hasNonNull("spec")) {
            applySpec(s, body.get("spec"));
            s.setVersion(s.getVersion() + 1);
        }
        if (body.hasNonNull("status") && (user.isAdmin() || user.isTeacher() || AiSkill.SCOPE_PERSONAL.equals(s.getScope()))) {
            String st = body.get("status").asText();
            if (!"ACTIVE".equals(st) && !"DISABLED".equals(st)) {
                throw new BusinessException("status 只能是 ACTIVE 或 DISABLED");
            }
            s.setStatus(st);
        }
        s.setUpdatedAt(LocalDateTime.now());
        s = skillRepo.save(s);
        if (body.hasNonNull("spec")) {
            snapshot(s, body.path("changelog").asText("修改"), user.getId());
        }
        return view(s, user);
    }

    @Transactional
    public void delete(Long id, AuthUser user) {
        AiSkill s = stored(id);
        if (!canManage(s, user)) {
            throw new BusinessException(403, "无权删除该 SKILL");
        }
        versionRepo.deleteBySkillId(id);
        skillRepo.delete(s);
    }

    /** 另存为:把任意可用 SKILL(内置/本站/自己的)复制成一个新的个人或本站 SKILL */
    @Transactional
    public Map<String, Object> duplicate(String sourceKey, JsonNode body, AuthUser user) {
        AiSkill src = resolveUsable(sourceKey, user);
        com.fasterxml.jackson.databind.node.ObjectNode req = om.createObjectNode();
        req.put("scope", body.path("scope").asText(AiSkill.SCOPE_PERSONAL));
        req.put("name", body.path("name").asText(src.getName() + "(副本)"));
        req.put("description", src.getDescription());
        req.put("icon", src.getIcon());
        req.put("category", src.getCategory());
        req.put("forkedFrom", src.getSkillKey());
        try {
            req.set("spec", om.readTree(src.getSpec()));
        } catch (Exception e) {
            throw new BusinessException(500, "源 SKILL 定义损坏");
        }
        return create(req, user);
    }

    /** 管理员把个人 SKILL 提升为本站共享 */
    @Transactional
    public Map<String, Object> promote(Long id, AuthUser admin) {
        AiSkill s = stored(id);
        s.setScope(AiSkill.SCOPE_TENANT);
        s.setOwnerUserId(null);
        s.setUpdatedAt(LocalDateTime.now());
        return view(skillRepo.save(s), admin);
    }

    public List<AiSkillVersion> versions(Long id, AuthUser user) {
        AiSkill s = stored(id);
        if (!canManage(s, user) && !AiSkill.SCOPE_TENANT.equals(s.getScope())) {
            throw new BusinessException(403, "无权查看");
        }
        return versionRepo.findBySkillIdOrderByVersionDesc(id);
    }

    public SkillSpec spec(AiSkill s) {
        return SkillSpec.parse(om, s.getSpec());
    }

    public Map<String, AiSkill> builtins() {
        return builtins;
    }

    // ---------- 内部 ----------

    private AiSkill stored(Long id) {
        return skillRepo.findById(id).orElseThrow(() -> new BusinessException(404, "SKILL 不存在"));
    }

    private boolean canManage(AiSkill s, AuthUser user) {
        if (user.isAdmin()) {
            return true;
        }
        if (AiSkill.SCOPE_PERSONAL.equals(s.getScope())) {
            return user.getId().equals(s.getOwnerUserId());
        }
        return user.isTeacher() && (s.getCreatedBy() == null || user.getId().equals(s.getCreatedBy()));
    }

    private void applyMeta(AiSkill s, JsonNode body) {
        if (body.hasNonNull("name")) {
            String name = body.get("name").asText().trim();
            if (name.isEmpty() || name.length() > 60) {
                throw new BusinessException("名称需为 1~60 字");
            }
            s.setName(name);
        }
        if (s.getName() == null) {
            throw new BusinessException("名称不能为空");
        }
        if (body.has("description")) {
            String d = body.path("description").asText("");
            s.setDescription(d.length() > 300 ? d.substring(0, 300) : d);
        }
        if (body.has("icon")) {
            String icon = body.path("icon").asText("✨");
            s.setIcon(icon.length() > 10 ? icon.substring(0, 10) : icon);
        }
        if (body.has("category")) {
            String c = body.path("category").asText("");
            s.setCategory(c.length() > 30 ? c.substring(0, 30) : c);
        }
    }

    private void applySpec(AiSkill s, JsonNode specNode) {
        if (specNode == null || specNode.isNull()) {
            throw new BusinessException("缺少 SKILL 定义 spec");
        }
        SkillSpec spec = SkillSpec.parse(om, specNode.toString());
        spec.validate(toolNames());
        s.setSpec(spec.raw().toString());
    }

    private void snapshot(AiSkill s, String changelog, Long by) {
        AiSkillVersion v = new AiSkillVersion();
        v.setSkillId(s.getId());
        v.setVersion(s.getVersion());
        v.setSpec(s.getSpec());
        v.setChangelog(changelog);
        v.setCreatedBy(by);
        versionRepo.save(v);
    }

    public Map<String, Object> view(AiSkill s, AuthUser viewer) {
        SkillSpec spec = spec(s);
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("id", s.getId());
        m.put("key", s.getSkillKey());
        m.put("name", s.getName());
        m.put("description", s.getDescription());
        m.put("icon", s.getIcon());
        m.put("category", s.getCategory());
        m.put("scope", s.getScope());
        m.put("ownerUserId", s.getOwnerUserId());
        m.put("status", s.getStatus());
        m.put("version", s.getVersion());
        m.put("forkedFrom", s.getForkedFrom());
        m.put("tools", spec.tools());
        m.put("outputMode", spec.outputMode());
        m.put("structuredInput", spec.hasStructuredInput());
        m.put("inputSchema", spec.inputSchema());
        m.put("greeting", spec.raw().path("greeting").asText(null));
        List<String> examples = new ArrayList<>();
        for (JsonNode e : spec.raw().path("examples")) {
            examples.add(e.asText());
        }
        m.put("examples", examples);
        m.put("editable", viewer != null && !AiSkill.SCOPE_BUILTIN.equals(s.getScope()) && canManage(s, viewer));
        m.put("updatedAt", s.getUpdatedAt());
        return m;
    }
}
