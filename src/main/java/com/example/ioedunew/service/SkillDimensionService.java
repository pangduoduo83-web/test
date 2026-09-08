package com.example.ioedunew.service;

import com.example.ioedunew.common.BusinessException;
import com.example.ioedunew.dto.MiscDtos;
import com.example.ioedunew.entity.Project;
import com.example.ioedunew.entity.SkillDimension;
import com.example.ioedunew.repository.ProjectRepository;
import com.example.ioedunew.repository.SkillDimensionRepository;
import com.example.ioedunew.repository.SkillScoreEventRepository;
import com.example.ioedunew.repository.SkillScoreRepository;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * 技能维度管理:雷达图顶点由本服务维护的 skill_dimensions 表决定,不再硬编码。
 * 维度名同时被 skill_scores、skill_score_events 与项目的 skillRequirements 引用,改名时统一级联。
 */
@Service
public class SkillDimensionService {

    /** 首次运行(或迁移脚本未执行)时用于初始化的默认维度说明 */
    private static final Map<String, String> DEFAULT_DESCRIPTIONS = new LinkedHashMap<>();

    static {
        DEFAULT_DESCRIPTIONS.put("嵌入式开发", "微控制器编程、外设驱动开发、实时操作系统等核心技能");
        DEFAULT_DESCRIPTIONS.put("编程能力", "C/C++、Python 等编程语言,数据结构与算法基础");
        DEFAULT_DESCRIPTIONS.put("通信技术", "有线/无线通信协议栈、组网与协议分析能力");
        DEFAULT_DESCRIPTIONS.put("PCB设计", "电路原理图设计、PCB 布局布线、信号完整性分析");
        DEFAULT_DESCRIPTIONS.put("信号处理", "信号采集、数字滤波、频谱分析与算法实现");
        DEFAULT_DESCRIPTIONS.put("硬件调试", "仪器仪表使用、电路故障定位与焊接工艺");
    }

    private final SkillDimensionRepository dimensionRepository;
    private final SkillScoreRepository skillScoreRepository;
    private final SkillScoreEventRepository eventRepository;
    private final ProjectRepository projectRepository;
    private final ObjectMapper objectMapper;

    public SkillDimensionService(SkillDimensionRepository dimensionRepository,
                                 SkillScoreRepository skillScoreRepository,
                                 SkillScoreEventRepository eventRepository,
                                 ProjectRepository projectRepository,
                                 ObjectMapper objectMapper) {
        this.dimensionRepository = dimensionRepository;
        this.skillScoreRepository = skillScoreRepository;
        this.eventRepository = eventRepository;
        this.projectRepository = projectRepository;
        this.objectMapper = objectMapper;
    }

    /** 启用中的维度,按排序号;表为空时先写入默认维度 */
    @Transactional
    public List<SkillDimension> listEnabled() {
        ensureDefaults();
        return dimensionRepository.findByEnabledTrueOrderBySortOrderAscIdAsc();
    }

    public Set<String> enabledNames() {
        Set<String> names = new LinkedHashSet<>();
        for (SkillDimension d : listEnabled()) {
            names.add(d.getName());
        }
        return names;
    }

    /** 管理端列表:附带引用该维度的项目数,方便判断能否删除 */
    @Transactional
    public List<Map<String, Object>> listForAdmin() {
        ensureDefaults();
        Map<String, Integer> projectCounts = new HashMap<>();
        for (Project p : projectRepository.findAll()) {
            for (String name : requirementNames(p.getSkillRequirements())) {
                projectCounts.merge(name, 1, Integer::sum);
            }
        }
        List<Map<String, Object>> result = new ArrayList<>();
        for (SkillDimension d : dimensionRepository.findAllByOrderBySortOrderAscIdAsc()) {
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("id", d.getId());
            m.put("name", d.getName());
            m.put("description", d.getDescription());
            m.put("sortOrder", d.getSortOrder());
            m.put("enabled", d.getEnabled());
            m.put("projectCount", projectCounts.getOrDefault(d.getName(), 0));
            m.put("userCount", skillScoreRepository.countBySkillName(d.getName()));
            m.put("updatedAt", d.getUpdatedAt());
            result.add(m);
        }
        return result;
    }

    @Transactional
    public SkillDimension create(MiscDtos.SkillDimensionRequest req) {
        String name = cleanName(req.getName());
        if (dimensionRepository.existsByName(name)) {
            throw new BusinessException("技能维度「" + name + "」已存在");
        }
        SkillDimension d = new SkillDimension();
        d.setName(name);
        d.setDescription(cleanDescription(req.getDescription()));
        d.setSortOrder(req.getSortOrder() == null ? nextSortOrder() : req.getSortOrder());
        d.setEnabled(req.getEnabled() == null || req.getEnabled());
        return dimensionRepository.save(d);
    }

    /** 改名会级联到所有用户的技能分、变动流水与项目技能要求 */
    @Transactional
    public SkillDimension update(Long id, MiscDtos.SkillDimensionRequest req) {
        SkillDimension d = dimensionRepository.findById(id)
                .orElseThrow(() -> new BusinessException(404, "技能维度不存在"));
        if (req.getName() != null) {
            String name = cleanName(req.getName());
            if (!name.equals(d.getName())) {
                if (dimensionRepository.existsByName(name)) {
                    throw new BusinessException("技能维度「" + name + "」已存在");
                }
                skillScoreRepository.renameSkill(d.getName(), name);
                eventRepository.renameSkill(d.getName(), name);
                renameInProjects(d.getName(), name);
                d.setName(name);
            }
        }
        if (req.getDescription() != null) {
            d.setDescription(cleanDescription(req.getDescription()));
        }
        if (req.getSortOrder() != null) {
            d.setSortOrder(req.getSortOrder());
        }
        if (req.getEnabled() != null) {
            d.setEnabled(req.getEnabled());
        }
        d.setUpdatedAt(LocalDateTime.now());
        return dimensionRepository.save(d);
    }

    /** 删除会连同所有用户在该维度上的分数与流水一起清除;仍被项目引用时拒绝,建议改为停用 */
    @Transactional
    public void delete(Long id) {
        SkillDimension d = dimensionRepository.findById(id)
                .orElseThrow(() -> new BusinessException(404, "技能维度不存在"));
        int referenced = 0;
        for (Project p : projectRepository.findAll()) {
            if (requirementNames(p.getSkillRequirements()).contains(d.getName())) {
                referenced++;
            }
        }
        if (referenced > 0) {
            throw new BusinessException(409, "仍有 " + referenced + " 个项目的技能要求引用「" + d.getName()
                    + "」,请先在项目中移除,或改为停用该维度");
        }
        if (dimensionRepository.count() <= 1) {
            throw new BusinessException("至少需要保留一个技能维度");
        }
        skillScoreRepository.deleteBySkillName(d.getName());
        eventRepository.deleteBySkillName(d.getName());
        dimensionRepository.delete(d);
    }

    /** 解析项目 skillRequirements JSON 中的技能名 */
    public Set<String> requirementNames(String json) {
        Set<String> names = new LinkedHashSet<>();
        if (json == null || json.trim().isEmpty()) {
            return names;
        }
        try {
            JsonNode node = objectMapper.readTree(json);
            if (node.isArray()) {
                for (JsonNode n : node) {
                    String name = n.path("name").asText("").trim();
                    if (!name.isEmpty()) {
                        names.add(name);
                    }
                }
            }
        } catch (Exception ignored) {
        }
        return names;
    }

    private void renameInProjects(String oldName, String newName) {
        for (Project p : projectRepository.findAll()) {
            if (!requirementNames(p.getSkillRequirements()).contains(oldName)) {
                continue;
            }
            try {
                JsonNode node = objectMapper.readTree(p.getSkillRequirements());
                for (JsonNode n : node) {
                    if (n instanceof ObjectNode && oldName.equals(n.path("name").asText(""))) {
                        ((ObjectNode) n).put("name", newName);
                    }
                }
                p.setSkillRequirements(objectMapper.writeValueAsString(node));
                projectRepository.save(p);
            } catch (Exception ignored) {
            }
        }
    }

    private void ensureDefaults() {
        if (dimensionRepository.count() > 0) {
            return;
        }
        int order = 1;
        for (String name : AuthService.SKILL_DIMENSIONS) {
            SkillDimension d = new SkillDimension();
            d.setName(name);
            d.setDescription(DEFAULT_DESCRIPTIONS.get(name));
            d.setSortOrder(order++);
            dimensionRepository.save(d);
        }
    }

    private int nextSortOrder() {
        int max = 0;
        for (SkillDimension d : dimensionRepository.findAll()) {
            max = Math.max(max, d.getSortOrder() == null ? 0 : d.getSortOrder());
        }
        return max + 1;
    }

    private String cleanName(String raw) {
        String name = raw == null ? "" : raw.trim();
        if (name.isEmpty()) {
            throw new BusinessException("技能维度名称不能为空");
        }
        if (name.length() > 30) {
            throw new BusinessException("技能维度名称不能超过 30 字");
        }
        return name;
    }

    private String cleanDescription(String raw) {
        if (raw == null) {
            return null;
        }
        String v = raw.trim();
        if (v.isEmpty()) {
            return null;
        }
        return v.length() <= 200 ? v : v.substring(0, 200);
    }
}
